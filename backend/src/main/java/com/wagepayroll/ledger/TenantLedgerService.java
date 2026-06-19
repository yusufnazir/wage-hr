package com.wagepayroll.ledger;

import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantLedgerRowDto;
import com.wagepayroll.domain.ledger.TenantLedgerEntity;
import com.wagepayroll.domain.ledger.TenantLedgerRepository;
import com.wagepayroll.domain.org.TenantCompanyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantLedgerService {

	private final TenantLedgerRepository tenantLedgerRepository;
	private final TenantCompanyRepository companyRepository;

	public TenantLedgerService(TenantLedgerRepository tenantLedgerRepository, TenantCompanyRepository companyRepository) {
		this.tenantLedgerRepository = tenantLedgerRepository;
		this.companyRepository = companyRepository;
	}

	@Transactional(readOnly = true)
	public List<TenantLedgerRowDto> listForCompany(UUID tenantId, UUID companyId) {
		requireCompany(tenantId, companyId);
		return tenantLedgerRepository.findByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
				.map(this::toRow)
				.toList();
	}

	private void requireCompany(UUID tenantId, UUID companyId) {
		companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_COMPANY"));
	}

	private TenantLedgerRowDto toRow(TenantLedgerEntity e) {
		return new TenantLedgerRowDto(e.getId(), e.getCompanyId(), e.getPlatformLedgerTemplateId(), e.getCode(),
				e.getDescription(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}
}
