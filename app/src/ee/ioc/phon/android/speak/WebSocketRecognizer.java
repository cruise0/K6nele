package ee.ioc.phon.android.speak;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class WebSocketRecognizer extends RecognitionService {

    private static final String PROTOCOL = "";

    private static final String WS_ARGS =
            "?content-type=audio/x-raw,+layout=(string)interleaved,+rate=(int)16000,+format=(string)S16LE,+channels=(int)1";

    private RawAudioRecorder mRecorder;
    private Callback mListener;

    private Handler mHandlerResult;
    private Handler mHandlerError;
    private Handler mHandlerFinish;

    private volatile Looper mSendLooper;
    private volatile Handler mSendHandler;
    private Handler mVolumeHandler = new Handler();

    private Runnable mSendTask;
    private Runnable mShowVolumeTask;

    private WebSocket mWebSocket;


    /**
     * Opens the socket and starts recording and sending the recorded packages.
     */
    @Override
    protected void onStartListening(final Intent recognizerIntent, Callback listener) {
        Log.i("onStartListening");
        mListener = listener;

        mHandlerResult = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Response response = Response.parseResponse((String) msg.obj);
                    if (response instanceof Response.ResponseResult) {
                        Response.ResponseResult responseResult = (Response.ResponseResult) response;
                        ArrayList<String> hypotheses = responseResult.getHypotheses();
                        if (responseResult.isFinal()) {
                            onResults(toBundle(hypotheses));
                            // We stop listening unless the caller explicitly asks us to carry on,
                            // by setting EXTRA_UNLIMITED_DURATION=true
                            if (!recognizerIntent.getBooleanExtra(Extras.EXTRA_UNLIMITED_DURATION, false)) {
                                onStopListening(mListener);
                            }
                        } else {
                            // We fire this only if the caller wanted partial results
                            if (recognizerIntent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)) {
                                onPartialResults(toBundle(hypotheses));
                            }
                        }
                    } else if (response instanceof Response.ResponseMessage) {
                        Response.ResponseMessage responseMessage = (Response.ResponseMessage) response;
                        Log.i(responseMessage.getStatus() + ": " + responseMessage.getMessage());
                        onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY);
                    }
                } catch (Response.ResponseException e) {
                    Log.e((String) msg.obj, e);
                    onError(SpeechRecognizer.ERROR_SERVER);
                }
            }
        };

        mHandlerError = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Exception e = (Exception) msg.obj;
                Log.e("Socket error?", e);
                if (e instanceof TimeoutException) {
                    onError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT);
                } else {
                    onError(SpeechRecognizer.ERROR_NETWORK);
                }
            }
        };

        mHandlerFinish = new Handler() {
            @Override
            public void handleMessage(Message msg) {
            }
        };

        try {
            onReadyForSpeech(new Bundle());
            startRecord();
            onBeginningOfSpeech();
            startSocket(getWsServiceUrl(recognizerIntent) + "speech" + WS_ARGS + getQueryParams(recognizerIntent));
        } catch (IOException e) {
            onError(SpeechRecognizer.ERROR_AUDIO);
        }
    }

    /**
     * Stops the recording and closes the socket.
     */
    @Override
    protected void onCancel(Callback listener) {
        stopRecording0();
        if (mWebSocket != null && mWebSocket.isOpen()) {
            mWebSocket.end(); // TODO: or close?
        }
        // We are ready for new speech
        onReadyForSpeech(new Bundle());
    }

    /**
     * Stops the recording and informs the socket that no more packages are coming.
     */
    @Override
    protected void onStopListening(Callback listener) {
        stopRecording0();
        onEndOfSpeech();
    }


    // TODO: review this
    private void stopRecording0() {
        if (mSendHandler != null) mSendHandler.removeCallbacks(mSendTask);
        if (mVolumeHandler != null) mVolumeHandler.removeCallbacks(mShowVolumeTask);
        if (mWebSocket != null && mWebSocket.isOpen()) {
            mWebSocket.send("EOS");
        }

        if (mRecorder != null) {
            mRecorder.release();
        }

        if (mSendLooper != null) {
            mSendLooper.quit();
            mSendLooper = null;
        }
    }


    /**
     * Starts recording.
     *
     * @throws IOException if there was an error, e.g. another app is currently recording
     */
    private void startRecord() throws IOException {
        mRecorder = new RawAudioRecorder();
        if (mRecorder.getState() == RawAudioRecorder.State.ERROR) {
            throw new IOException();
        }

        if (mRecorder.getState() != RawAudioRecorder.State.READY) {
            throw new IOException();
        }

        mRecorder.start();

        if (mRecorder.getState() != RawAudioRecorder.State.RECORDING) {
            throw new IOException();
        }
    }


    /**
     * Opens the socket and starts recording/sending.
     *
     * @param url Webservice URL
     */
    private void startSocket(String url) {
        Log.i(url);

        AsyncHttpClient.getDefaultInstance().websocket(url, PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

            @Override
            public void onCompleted(Exception ex, final WebSocket webSocket) {
                if (ex != null) {
                    handleError(ex);
                    return;
                }

                mWebSocket = webSocket;
                startSending(webSocket);

                webSocket.setStringCallback(new WebSocket.StringCallback() {
                    public void onStringAvailable(String s) {
                        Log.i(s);
                        handleResult(s);
                    }
                });

                webSocket.setClosedCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        Log.e("ClosedCallback: ", ex);
                        if (ex == null) {
                            handleFinish();
                        } else {
                            handleError(ex);
                        }
                    }
                });

                webSocket.setEndCallback(new CompletedCallback() {
                    @Override
                    public void onCompleted(Exception ex) {
                        Log.e("EndCallback: ", ex);
                        if (ex == null) {
                            handleFinish();
                        } else {
                            handleError(ex);
                        }
                    }
                });
            }
        });
    }


    private void startSending(final WebSocket webSocket) {
        HandlerThread thread = new HandlerThread("SendHandlerThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mSendLooper = thread.getLooper();
        mSendHandler = new Handler(mSendLooper);

        // Send chunks to the server
        mSendTask = new Runnable() {
            public void run() {
                if (mRecorder != null) {
                    byte[] buffer = mRecorder.consumeRecordingAndTruncate();
                    webSocket.send(buffer);
                    mSendHandler.postDelayed(this, Constants.TASK_INTERVAL_IME_SEND);
                }
            }
        };

        // Monitor the volume level
        mShowVolumeTask = new Runnable() {
            public void run() {
                if (mRecorder != null) {
                    onRmsChanged(mRecorder.getRmsdb());
                    mVolumeHandler.postDelayed(this, Constants.TASK_INTERVAL_VOL);

                }
            }
        };

        mSendHandler.postDelayed(mSendTask, Constants.TASK_DELAY_IME_SEND);
        mVolumeHandler.postDelayed(mShowVolumeTask, Constants.TASK_DELAY_VOL);
    }

    private void handleResult(String text) {
        Message msg = new Message();
        msg.obj = text;
        mHandlerResult.sendMessage(msg);
    }

    private void handleError(Exception error) {
        // As soon as there is an error we shut down the socket and the recorder
        onCancel(mListener);

        Message msg = new Message();
        msg.obj = error;
        mHandlerError.sendMessage(msg);
    }

    // TODO: there does not seem to be an official call back for the socket closing
    private void handleFinish() {
        onCancel(mListener);
        //Message msg = new Message();
        //mHandlerFinish.sendMessage(msg);
    }

    private void onReadyForSpeech(Bundle bundle) {
        try {
            mListener.readyForSpeech(bundle);
        } catch (RemoteException e) {
        }
    }

    private void onRmsChanged(float rms) {
        try {
            mListener.rmsChanged(rms);
        } catch (RemoteException e) {
        }
    }

    private void onError(int errorCode) {
        try {
            mListener.error(errorCode);
        } catch (RemoteException e) {
        }
    }

    private void onResults(Bundle bundle) {
        try {
            mListener.results(bundle);
        } catch (RemoteException e) {
        }
    }

    private void onPartialResults(Bundle bundle) {
        try {
            mListener.partialResults(bundle);
        } catch (RemoteException e) {
        }
    }

    private void onBeginningOfSpeech() {
        try {
            mListener.beginningOfSpeech();
        } catch (RemoteException e) {
        }
    }

    private void onEndOfSpeech() {
        try {
            mListener.endOfSpeech();
        } catch (RemoteException e) {
        }
    }

    private String getWsServiceUrl(Intent intent) {
        String url = intent.getStringExtra(Extras.EXTRA_SERVER_URL);
        if (url == null) {
            return getResources().getString(R.string.defaultWsService);
        }
        return url;
    }

    /**
     * Extracts the editor info, and uses
     * ChunkedWebRecSessionBuilder to extract some additional extras.
     * TODO: unify this better
     */
    private String getQueryParams(Intent intent) {
        List<BasicNameValuePair> list = new ArrayList<>();
        flattenBundle("editorInfo_", list, intent.getBundleExtra(Extras.EXTRA_EDITOR_INFO));

        try {
            ChunkedWebRecSessionBuilder builder = new ChunkedWebRecSessionBuilder(this, intent.getExtras(), null);
            if (Log.DEBUG) Log.i(builder.toStringArrayList());
            // TODO: review these parameter names
            listAdd(list, "lang", builder.getLang());
            listAdd(list, "lm", toString(builder.getGrammarUrl()));
            listAdd(list, "output-lang", builder.getGrammarTargetLang());
            listAdd(list, "user-agent", builder.getUserAgentComment());
            listAdd(list, "calling-package", builder.getCaller());
            listAdd(list, "user-id", builder.getDeviceId());
            listAdd(list, "partial", "" + builder.isPartialResults());
        } catch (MalformedURLException e) {
        }

        if (list.size() == 0) {
            return "";
        }
        return "&" + URLEncodedUtils.format(list, "utf-8");
    }


    private static boolean listAdd(List<BasicNameValuePair> list, String key, String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        return list.add(new BasicNameValuePair(key, value));
    }

    private static void flattenBundle(String prefix, List<BasicNameValuePair> list, Bundle bundle) {
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value != null) {
                    if (value instanceof Bundle) {
                        flattenBundle(prefix + key + "_", list, bundle);
                    } else {
                        list.add(new BasicNameValuePair(prefix + key, toString(value)));
                    }
                }
            }
        }
    }

    private static Bundle toBundle(ArrayList<String> hypotheses) {
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, hypotheses);
        return bundle;
    }

    // TODO: replace by a built-in
    private static String toString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }
}