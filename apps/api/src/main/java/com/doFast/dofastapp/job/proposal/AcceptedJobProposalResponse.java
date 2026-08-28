package com.doFast.dofastapp.job.proposal;

import com.doFast.dofastapp.job.dto.JobResponse;

public record AcceptedJobProposalResponse(
        JobResponse job,
        JobProposalResponse proposal
) {}
