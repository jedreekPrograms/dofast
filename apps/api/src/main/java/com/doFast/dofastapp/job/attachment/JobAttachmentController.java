package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.user.entity.User;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/jobs/{jobId}/attachments")
public class JobAttachmentController {

    private final JobAttachmentService attachmentService;

    public JobAttachmentController(JobAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobAttachmentResponse upload(
            @PathVariable Long jobId,
            @RequestParam JobAttachmentVisibility visibility,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        return attachmentService.upload(jobId, visibility, file, user);
    }

    @GetMapping
    public List<JobAttachmentResponse> list(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return attachmentService.listVisible(jobId, user);
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> download(
            @PathVariable Long jobId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal User user
    ) {
        JobAttachmentContent content = attachmentService.download(jobId, attachmentId, user);
        JobAttachmentResponse metadata = content.metadata();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.mediaType()));
        headers.setContentLength(content.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(metadata.originalFilename(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long jobId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal User user
    ) {
        attachmentService.delete(jobId, attachmentId, user);
    }
}
