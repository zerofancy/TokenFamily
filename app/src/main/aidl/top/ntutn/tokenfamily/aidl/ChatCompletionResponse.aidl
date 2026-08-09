package top.ntutn.tokenfamily.aidl;

parcelable ChatCompletionResponse {
    int statusCode;
    String statusMessage;
    String contentType;
    String body;
    List<String> headerNames;
    List<String> headerValues;
}
