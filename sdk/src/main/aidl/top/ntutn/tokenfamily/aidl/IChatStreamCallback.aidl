package top.ntutn.tokenfamily.aidl;

oneway interface IChatStreamCallback {
    void onChunk(String requestId, String chunk);
    void onComplete(String requestId);
    void onError(String requestId, String errorCode, String errorMessage);
}
