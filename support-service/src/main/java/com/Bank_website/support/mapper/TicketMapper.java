package com.Bank_website.support.mapper;

import com.Bank_website.support.dto.TicketListResponse;
import com.Bank_website.support.dto.TicketMessageResponse;
import com.Bank_website.support.dto.TicketResponse;
import com.Bank_website.support.entity.SupportTicket;
import com.Bank_website.support.entity.TicketMessage;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/** The only place where entities are converted to DTOs. */
@Component
public class TicketMapper {

    public TicketResponse toResponse(SupportTicket t, List<TicketMessage> messages) {
        return new TicketResponse(t.getTicketId(), t.getCustomerId(), t.getAssignedAgentId(), t.getCategory(),
                t.getPriority(), t.getStatus(), t.getSubject(), t.getRelatedTransactionId(), t.getCreatedAt(),
                t.getResolvedAt(), messages == null ? null : messages.stream().map(this::toMessageResponse).toList());
    }

    public TicketResponse toSummary(SupportTicket t) {
        return toResponse(t, null);
    }

    public TicketMessageResponse toMessageResponse(TicketMessage m) {
        return new TicketMessageResponse(m.getMessageId(), m.getTicketId(), m.getSenderId(), m.getSenderType(),
                m.getMessageBody(), m.isInternalNote(), m.getCreatedAt());
    }

    public TicketListResponse toListResponse(Page<SupportTicket> page) {
        return new TicketListResponse(page.getContent().stream().map(this::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
