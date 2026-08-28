package com.doFast.dofastapp.job.attachment;

public interface AttachmentStorage {
    void store(String storageKey, byte[] plaintext);
    byte[] read(String storageKey);
    void delete(String storageKey);
}
