package top.ntutn.tokenfamily.aidl;

parcelable ChatCompletionResponse {
    String id;
    String model;
    String content;
    int promptTokens;
    int completionTokens;
    int totalTokens;
    String errorCode;
    String errorMessage;
}
