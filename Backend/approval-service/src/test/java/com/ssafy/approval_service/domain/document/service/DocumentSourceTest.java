package com.ssafy.approval_service.domain.document.service;

import com.ssafy.approval_service.domain.approval.model.entity.enums.LineRole;
import com.ssafy.approval_service.domain.approval.repository.ApprovalHistoryRepository;
import com.ssafy.approval_service.domain.approval.repository.ApprovalLineRepository;
import com.ssafy.approval_service.domain.approval.service.ApprovalLineValidator;
import com.ssafy.approval_service.domain.document.model.dto.ApprovalLineRequest;
import com.ssafy.approval_service.domain.document.model.dto.DocumentCreateRequest;
import com.ssafy.approval_service.domain.document.model.entity.Document;
import com.ssafy.approval_service.domain.document.model.entity.enums.FormType;
import com.ssafy.approval_service.domain.document.model.entity.enums.SourceType;
import com.ssafy.approval_service.domain.document.repository.DocumentRepository;
import com.ssafy.approval_service.global.exception.BusinessException;
import com.ssafy.approval_service.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 문서 생성 시 출처(sourceType/sourceId) 검증과 저장 */
class DocumentSourceTest {

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentServiceImplV1 service = new DocumentServiceImplV1(
            documentRepository,
            mock(ApprovalLineRepository.class),
            mock(ApprovalHistoryRepository.class),
            mock(ApprovalLineValidator.class));

    private static final List<ApprovalLineRequest> LINES =
            List.of(new ApprovalLineRequest(2L, (short) 1, LineRole.APPROVER));

    private static DocumentCreateRequest request(SourceType sourceType, Long sourceId) {
        return new DocumentCreateRequest("DOC-1", "휴가 신청", FormType.LEAVE, LINES, sourceType, sourceId);
    }

    private Document savedDocument() {
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 출처를_생략하면_직접_기안으로_저장된다() {
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(request(null, null), 1L);

        Document saved = savedDocument();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.DIRECT);
        assertThat(saved.getSourceId()).isNull();
    }

    @Test
    void 다른_서비스에서_온_문서는_출처와_원본_ID가_저장된다() {
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(request(SourceType.LEAVE, 42L), 1L);

        Document saved = savedDocument();
        assertThat(saved.getSourceType()).isEqualTo(SourceType.LEAVE);
        assertThat(saved.getSourceId()).isEqualTo(42L);
    }

    @Test
    void 다른_서비스에서_왔는데_원본_ID가_없으면_400() {
        assertThatThrownBy(() -> service.create(request(SourceType.LEAVE, null), 1L))
                .isInstanceOf(BusinessException.class)
                .matches(t -> ((BusinessException) t).getErrorCode() == ErrorCode.INVALID_DOCUMENT_SOURCE);
        verifyNoInteractions(documentRepository);
    }

    @Test
    void 직접_기안인데_원본_ID가_있으면_400() {
        assertThatThrownBy(() -> service.create(request(SourceType.DIRECT, 42L), 1L))
                .isInstanceOf(BusinessException.class)
                .matches(t -> ((BusinessException) t).getErrorCode() == ErrorCode.INVALID_DOCUMENT_SOURCE);
        verifyNoInteractions(documentRepository);
    }
}
