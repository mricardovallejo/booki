package com.booki.it;

import com.booki.dto.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Document lifecycle over HTTP: upload (real PDF parsing via PDFBox), listing
 * scoping, download bytes, delete cascade and cross-user 404s. Covers
 * {@code DocumentController}, {@code DocumentServiceImpl}, storage adapter,
 * and the PDF plumbing in one pass.
 */
class DocumentIT extends IntegrationTestBase {

    @Test
    void uploadParsesPagesAndListsScopedToOwner() {
        AuthData owner = register(uniqueEmail("owner"));
        AuthData other = register(uniqueEmail("other"));

        Long documentId = uploadDocument(owner, pdfWithPages("Alpha page", "Beta page", "Gamma page"), "report.pdf");

        ResponseEntity<List<DocumentResponse>> ownerList = rest.exchange(
                "/api/documents", HttpMethod.GET, new HttpEntity<>(auth(owner.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(ownerList.getBody())
                .extracting(DocumentResponse::getId, DocumentResponse::getPageCount, DocumentResponse::getTitle)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(documentId, 3, "report.pdf"));

        ResponseEntity<List<DocumentResponse>> otherList = rest.exchange(
                "/api/documents", HttpMethod.GET, new HttpEntity<>(auth(other.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(otherList.getBody()).isEmpty();
    }

    @Test
    void uploadRejectsNonPdfBytes() {
        AuthData user = register(uniqueEmail("pdf"));

        ResponseEntity<Map<String, String>> response = rest.exchange("/api/documents", HttpMethod.POST,
                new HttpEntity<>(multipart("file", "just text, not a pdf".getBytes(), "notes.pdf", org.springframework.http.MediaType.TEXT_PLAIN), auth(user.token())),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Only PDF files are accepted");
    }

    @Test
    void ownerDownloadsOriginalBytesWhileForeignerGets404() {
        AuthData owner = register(uniqueEmail("dl"));
        AuthData other = register(uniqueEmail("dl-other"));
        byte[] pdf = pdfWithPages("Download me");
        Long documentId = uploadDocument(owner, pdf, "original.pdf");

        ResponseEntity<byte[]> download = rest.exchange("/api/documents/" + documentId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth(owner.token())), byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isEqualTo(pdf);

        ResponseEntity<byte[]> foreign = rest.exchange("/api/documents/" + documentId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth(other.token())), byte[].class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteRemovesDocumentAndForeignDeleteIs404() {
        AuthData owner = register(uniqueEmail("del"));
        AuthData other = register(uniqueEmail("del-other"));
        Long documentId = uploadDocument(owner, pdfWithPages("Temporary"), "temp.pdf");

        ResponseEntity<Void> foreignDelete = rest.exchange("/api/documents/" + documentId, HttpMethod.DELETE,
                new HttpEntity<>(auth(other.token())), Void.class);
        assertThat(foreignDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> delete = rest.exchange("/api/documents/" + documentId, HttpMethod.DELETE,
                new HttpEntity<>(auth(owner.token())), Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<DocumentResponse> getAfter = rest.exchange("/api/documents/" + documentId, HttpMethod.GET,
                new HttpEntity<>(auth(owner.token())), DocumentResponse.class);
        assertThat(getAfter.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
