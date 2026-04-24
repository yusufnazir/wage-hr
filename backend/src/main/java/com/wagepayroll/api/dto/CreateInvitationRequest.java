package com.wagepayroll.api.dto;

import java.util.UUID;

public record CreateInvitationRequest(String email, UUID roleId) {
}
