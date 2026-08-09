package top.ntutn.tokenfamily.aidl;

parcelable ChatCompletionRequest {
    String requestId;
    String bodyJson;
    List<String> headerNames;
    List<String> headerValues;
}
