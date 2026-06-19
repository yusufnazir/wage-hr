package com.wagepayroll.payroll;

import java.util.List;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantPayrollYtdAccumulatorRowDto;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorEntity;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantEmployeePayrollYtdService {

	private final TenantEmployeeRepository employeeRepository;
	private final TenantPayrollYtdAccumulatorRepository ytdRepository;

	public TenantEmployeePayrollYtdService(TenantEmployeeRepository employeeRepository,
			TenantPayrollYtdAccumulatorRepository ytdRepository) {
		this.employeeRepository = employeeRepository;
		this.ytdRepository = ytdRepository;
	}

	@Transactional(readOnly = true)
	public List<TenantPayrollYtdAccumulatorRowDto> listForEmployee(UUID tenantId, UUID employeeId, int taxYear) {
		if (taxYear < 1900 || taxYear > 2200) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxYear must be a valid calendar year");
		}
		employeeRepository.findByIdAndTenantId(employeeId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
		return ytdRepository.findByTenantIdAndEmployeeIdAndTaxYearOrderByAccumulatorCodeAsc(tenantId, employeeId, taxYear)
				.stream()
				.map(TenantEmployeePayrollYtdService::toDto)
				.toList();
	}

	private static TenantPayrollYtdAccumulatorRowDto toDto(TenantPayrollYtdAccumulatorEntity e) {
		return new TenantPayrollYtdAccumulatorRowDto(e.getAccumulatorCode(), e.getTaxYear(), e.getAmount(),
				e.getCurrencyIso3(), e.getUpdatedAt());
	}
}
