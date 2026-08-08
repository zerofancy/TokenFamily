package top.ntutn.tokenfamily.aidl;

import top.ntutn.tokenfamily.aidl.ChatMessage;

parcelable ChatCompletionRequest {
    String requestId;
    String model;
    List<ChatMessage> messages;
    double temperature = 1.0;
    double topP = 1.0;
    int maxTokens = 0;
    boolean stream = false;
}
