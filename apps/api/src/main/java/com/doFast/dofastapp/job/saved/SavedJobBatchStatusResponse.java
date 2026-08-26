package com.doFast.dofastapp.job.saved;

import java.util.Set;

public record SavedJobBatchStatusResponse(Set<Long> savedJobIds) {
}
