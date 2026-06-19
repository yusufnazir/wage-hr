package com.wagepayroll.employeepayment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.wagepayroll.api.dto.TenantEmployeePayPeriodPaymentRowDto;
import com.wagepayroll.api.dto.TenantEmployeePaymentDestinationPutItem;
import com.wagepayroll.api.dto.TenantEmployeePaymentDestinationRowDto;
import com.wagepayroll.api.dto.TenantEmployeePaymentOverviewDto;
import com.wagepayroll.api.dto.TenantEmployeePaymentPeriodGroupDto;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateEntity;
import com.wagepayroll.domain.banktemplate.TenantBankTemplateRepository;
import com.wagepayroll.domain.employeepayment.TenantEmployeePayPeriodPaymentEntity;
import com.wagepayroll.domain.employeepayment.TenantEmployeePayPeriodPaymentRepository;
import com.wagepayroll.domain.employeepayment.TenantEmployeePaymentDestinationEntity;
import com.wagepayroll.domain.employeepayment.TenantEmployeePaymentDestinationRepository;
import com.wagepayroll.domain.org.TenantCompanyEntity;
import com.wagepayroll.domain.org.TenantCompanyRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.domain.paymentlocation.TenantPaymentLocationEntity;
import com.wagepayroll.domain.paymentlocation.TenantPaymentLocationRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantEmployeePaymentService {

	private static final java.util.Set<String> CHANNEL_TYPES = java.util.Set.of("BANK", "CASH");
	private static final java.util.Set<String> SPLIT_TYPES = java.util.Set.of("PERCENT", "AMOUNT");

	private final TenantEmployeePaymentDestinationRepository destinationRepository;
	private final TenantEmployeePayPeriodPaymentRepository periodPaymentRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final TenantCompanyRepository companyRepository;
	private final TenantPaymentLocationRepository paymentLocationRepository;
	private final TenantBankTemplateRepository bankTemplateRepository;
	private final TenantPayPeriodRepository payPeriodRepository;

	public TenantEmployeePaymentService(TenantEmployeePaymentDestinationRepository destinationRepository,
			TenantEmployeePayPeriodPaymentRepository periodPaymentRepository,
			TenantEmployeeRepository employeeRepository, TenantCompanyRepository companyRepository,
			TenantPaymentLocationRepository paymentLocationRepository,
			TenantBankTemplateRepository bankTemplateRepository, TenantPayPeriodRepository payPeriodRepository) {
		this.destinationRepository = destinationRepository;
		this.periodPaymentRepository = periodPaymentRepository;
		this.employeeRepository = employeeRepository;
		this.companyRepository = companyRepository;
		this.paymentLocationRepository = paymentLocationRepository;
		this.bankTemplateRepository = bankTemplateRepository;
		this.payPeriodRepository = payPeriodRepository;
	}

	@Transactional(readOnly = true)
	public TenantEmployeePaymentOverviewDto overview(UUID tenantId, UUID employeeId) {
		TenantEmployeeEntity employee = requireEmployee(tenantId, employeeId);
		TenantCompanyEntity company = requireCompany(tenantId, employee.getCompanyId());
		List<TenantEmployeePaymentDestinationRowDto> destinations = destinationRepository
				.findByTenantIdAndEmployeeIdOrderBySortOrderAsc(tenantId, employeeId).stream()
				.map(e -> toDestinationRow(e, resolveLocationName(tenantId, e.getPaymentLocationId()),
						resolveBankName(tenantId, e.getBankTemplateId())))
				.toList();

		TenantEmployeePaymentPeriodGroupDto active = buildActivePeriodGroup(tenantId, employeeId, company);
		List<TenantEmployeePaymentPeriodGroupDto> closed = new ArrayList<>();

		Map<UUID, TenantPayPeriodEntity> periodById = new LinkedHashMap<>();
		for (TenantEmployeePayPeriodPaymentEntity row : periodPaymentRepository
				.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employeeId)) {
			periodById.putIfAbsent(row.getPayPeriodId(),
					payPeriodRepository.findByIdAndTenantId(row.getPayPeriodId(), tenantId).orElse(null));
		}
		for (Map.Entry<UUID, TenantPayPeriodEntity> e : periodById.entrySet()) {
			TenantPayPeriodEntity period = e.getValue();
			if (period != null && "CLOSED".equals(period.getStatus())) {
				if (active.payPeriodId() == null || !period.getId().equals(active.payPeriodId())) {
					closed.add(toPeriodGroup(tenantId, employeeId, period, null));
				}
			}
		}
		closed.sort(Comparator.comparing(TenantEmployeePaymentPeriodGroupDto::endDate).reversed());

		return new TenantEmployeePaymentOverviewDto(destinations, active, closed);
	}

	@Transactional(readOnly = true)
	public Page<TenantEmployeePayPeriodPaymentRowDto> listPaymentHistory(UUID tenantId, UUID employeeId, Integer year,
			UUID payPeriodId, int page, int size) {
		requireEmployee(tenantId, employeeId);
		int safeSize = Math.min(Math.max(size, 1), 100);
		Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.unsorted());
		return periodPaymentRepository.findClosedHistory(tenantId, employeeId, year, payPeriodId, pageable)
				.map(row -> toPaymentRow(tenantId, row));
	}

	@Transactional
	public List<TenantEmployeePaymentDestinationRowDto> replaceDestinations(UUID tenantId, UUID employeeId,
			List<TenantEmployeePaymentDestinationPutItem> items) {
		TenantEmployeeEntity employee = requireEmployee(tenantId, employeeId);
		if (items == null) {
			throw badRequest("items is required");
		}
		validateDestinationItems(tenantId, employee.getCompanyId(), items);
		destinationRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		Instant now = Instant.now();
		int order = 0;
		List<TenantEmployeePaymentDestinationEntity> saved = new ArrayList<>();
		for (TenantEmployeePaymentDestinationPutItem item : items) {
			if (item.active() != null && !item.active()) {
				continue;
			}
			TenantEmployeePaymentDestinationEntity e = new TenantEmployeePaymentDestinationEntity();
			e.setId(UUID.randomUUID());
			e.setTenantId(tenantId);
			e.setCompanyId(employee.getCompanyId());
			e.setEmployeeId(employeeId);
			e.setChannelType(normalizeChannel(item.channelType()));
			e.setPaymentLocationId(item.paymentLocationId());
			e.setBankTemplateId(item.bankTemplateId());
			e.setAccountNumber(trim(item.accountNumber()));
			e.setCurrency(normalizeCurrency(item.currency()));
			e.setSplitType(normalizeSplit(item.splitType()));
			e.setSplitValue(item.splitValue().setScale(4, RoundingMode.HALF_UP));
			e.setSortOrder(item.sortOrder() != null ? item.sortOrder() : order++);
			e.setActive(true);
			e.setCreatedAt(now);
			e.setUpdatedAt(now);
			saved.add(destinationRepository.save(e));
		}
		return saved.stream()
				.map(row -> toDestinationRow(row, resolveLocationName(tenantId, row.getPaymentLocationId()),
						resolveBankName(tenantId, row.getBankTemplateId())))
				.toList();
	}

	@Transactional
	public void materializePaymentsForFinalize(UUID tenantId, UUID companyId, UUID payPeriodId, UUID payPeriodRunId,
			Map<UUID, BigDecimal> employeeNetPay) {
		if (employeeNetPay == null || employeeNetPay.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (Map.Entry<UUID, BigDecimal> entry : employeeNetPay.entrySet()) {
			UUID employeeId = entry.getKey();
			BigDecimal net = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
			if (periodPaymentRepository.existsByTenantIdAndPayPeriodRunIdAndEmployeeId(tenantId, payPeriodRunId,
					employeeId)) {
				continue;
			}
			List<TenantEmployeePaymentDestinationEntity> destinations = destinationRepository
					.findByTenantIdAndEmployeeIdAndActiveTrueOrderBySortOrderAsc(tenantId, employeeId);
			if (destinations.isEmpty()) {
				continue;
			}
			Map<UUID, BigDecimal> allocated = allocate(net, destinations);
			for (TenantEmployeePaymentDestinationEntity dest : destinations) {
				BigDecimal amount = allocated.getOrDefault(dest.getId(), BigDecimal.ZERO);
				if (amount.compareTo(BigDecimal.ZERO) == 0) {
					continue;
				}
				TenantEmployeePayPeriodPaymentEntity row = new TenantEmployeePayPeriodPaymentEntity();
				row.setId(UUID.randomUUID());
				row.setTenantId(tenantId);
				row.setCompanyId(companyId);
				row.setEmployeeId(employeeId);
				row.setPayPeriodId(payPeriodId);
				row.setPayPeriodRunId(payPeriodRunId);
				row.setDestinationId(dest.getId());
				row.setChannelType(dest.getChannelType());
				row.setPaymentLocationId(dest.getPaymentLocationId());
				row.setPaymentLocationName(resolveLocationName(tenantId, dest.getPaymentLocationId()));
				row.setBankTemplateId(dest.getBankTemplateId());
				row.setBankName(resolveBankName(tenantId, dest.getBankTemplateId()));
				row.setAccountNumber(dest.getAccountNumber());
				row.setCurrency(dest.getCurrency());
				row.setSplitType(dest.getSplitType());
				row.setSplitValue(dest.getSplitValue());
				row.setAllocatedAmount(amount.setScale(4, RoundingMode.HALF_UP));
				row.setCreatedAt(now);
				periodPaymentRepository.save(row);
			}
		}
	}

	private Map<UUID, BigDecimal> allocate(BigDecimal net,
			List<TenantEmployeePaymentDestinationEntity> destinations) {
		BigDecimal remaining = net.setScale(4, RoundingMode.HALF_UP);
		Map<UUID, BigDecimal> out = new LinkedHashMap<>();
		List<TenantEmployeePaymentDestinationEntity> amounts = destinations.stream()
				.filter(d -> "AMOUNT".equals(d.getSplitType())).toList();
		List<TenantEmployeePaymentDestinationEntity> percents = destinations.stream()
				.filter(d -> "PERCENT".equals(d.getSplitType())).toList();
		for (TenantEmployeePaymentDestinationEntity d : amounts) {
			BigDecimal slice = d.getSplitValue().min(remaining).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
			out.put(d.getId(), slice);
			remaining = remaining.subtract(slice);
		}
		if (!percents.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal percentTotal = percents.stream().map(TenantEmployeePaymentDestinationEntity::getSplitValue)
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			if (percentTotal.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal assigned = BigDecimal.ZERO;
				for (int i = 0; i < percents.size(); i++) {
					TenantEmployeePaymentDestinationEntity d = percents.get(i);
					BigDecimal slice;
					if (i == percents.size() - 1) {
						slice = remaining.subtract(assigned).max(BigDecimal.ZERO);
					}
					else {
						slice = remaining.multiply(d.getSplitValue()).divide(percentTotal, 4, RoundingMode.HALF_UP);
						assigned = assigned.add(slice);
					}
					out.merge(d.getId(), slice, BigDecimal::add);
				}
			}
		}
		return out;
	}

	private TenantEmployeePaymentPeriodGroupDto buildActivePeriodGroup(UUID tenantId, UUID employeeId,
			TenantCompanyEntity company) {
		if (company.getCurrentYear() == null || company.getPayPeriodEndDate() == null) {
			return new TenantEmployeePaymentPeriodGroupDto(null, 0, "", "", "OPEN", null, List.of());
		}
		return payPeriodRepository
				.findByTenantIdAndCompanyIdAndYearAndEndDate(tenantId, company.getId(), company.getCurrentYear(),
						company.getPayPeriodEndDate())
				.map(p -> toPeriodGroup(tenantId, employeeId, p, company.getCurrentPeriod()))
				.orElseGet(() -> new TenantEmployeePaymentPeriodGroupDto(null, company.getCurrentYear(), "",
						company.getPayPeriodEndDate().toString(), "OPEN", company.getCurrentPeriod(), List.of()));
	}

	private TenantEmployeePayPeriodPaymentRowDto toPaymentRow(UUID tenantId,
			TenantEmployeePayPeriodPaymentEntity row) {
		TenantPayPeriodEntity period = payPeriodRepository.findByIdAndTenantId(row.getPayPeriodId(), tenantId)
				.orElse(null);
		int payPeriodYear = period != null ? period.getYear() : 0;
		String start = period != null ? period.getStartDate().toString() : "";
		String end = period != null ? period.getEndDate().toString() : "";
		String status = period != null ? period.getStatus() : "";
		return new TenantEmployeePayPeriodPaymentRowDto(row.getId(), row.getPayPeriodId(), row.getPayPeriodRunId(),
				payPeriodYear, start, end, status, row.getChannelType(), row.getPaymentLocationId(),
				row.getPaymentLocationName(), row.getBankTemplateId(), row.getBankName(), row.getAccountNumber(),
				row.getCurrency(), row.getSplitType(), row.getSplitValue(), row.getAllocatedAmount());
	}

	private TenantEmployeePaymentPeriodGroupDto toPeriodGroup(UUID tenantId, UUID employeeId,
			TenantPayPeriodEntity period, Integer periodNumber) {
		List<TenantEmployeePayPeriodPaymentRowDto> payments = periodPaymentRepository
				.findByTenantIdAndEmployeeIdAndPayPeriodIdOrderByCreatedAtAsc(tenantId, employeeId, period.getId())
				.stream()
				.map(row -> toPaymentRow(tenantId, row))
				.toList();
		return new TenantEmployeePaymentPeriodGroupDto(period.getId(), period.getYear(),
				period.getStartDate().toString(), period.getEndDate().toString(), period.getStatus(), periodNumber,
				payments);
	}

	private void validateDestinationItems(UUID tenantId, UUID companyId,
			List<TenantEmployeePaymentDestinationPutItem> items) {
		BigDecimal percentSum = BigDecimal.ZERO;
		for (TenantEmployeePaymentDestinationPutItem item : items) {
			if (item.active() != null && !item.active()) {
				continue;
			}
			String channel = normalizeChannel(item.channelType());
			if ("CASH".equals(channel)) {
				if (item.paymentLocationId() == null) {
					throw badRequest("paymentLocationId is required for CASH");
				}
				TenantPaymentLocationEntity loc = paymentLocationRepository
						.findByIdAndTenantId(item.paymentLocationId(), tenantId)
						.orElseThrow(() -> badRequest("Unknown payment location"));
				if (!loc.getCompanyId().equals(companyId) || !"CASH".equals(loc.getPaymentType())) {
					throw badRequest("Payment location must be an active CASH location for this company");
				}
			}
			else {
				if (item.bankTemplateId() == null) {
					throw badRequest("bankTemplateId is required for BANK");
				}
				if (item.accountNumber() == null || item.accountNumber().isBlank()) {
					throw badRequest("accountNumber is required for BANK");
				}
				bankTemplateRepository.findByIdAndTenantId(item.bankTemplateId(), tenantId)
						.orElseThrow(() -> badRequest("Unknown bank template"));
			}
			if ("PERCENT".equals(normalizeSplit(item.splitType()))) {
				percentSum = percentSum.add(item.splitValue());
			}
		}
		if (percentSum.compareTo(new BigDecimal("100")) > 0) {
			throw badRequest("PERCENT splits cannot exceed 100% in total");
		}
	}

	private TenantEmployeePaymentDestinationRowDto toDestinationRow(TenantEmployeePaymentDestinationEntity e,
			String locationName, String bankName) {
		return new TenantEmployeePaymentDestinationRowDto(e.getId(), e.getCompanyId(), e.getEmployeeId(),
				e.getChannelType(), e.getPaymentLocationId(), locationName, e.getBankTemplateId(), bankName,
				e.getAccountNumber(), e.getCurrency(), e.getSplitType(), e.getSplitValue(), e.getSortOrder(),
				e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private String resolveLocationName(UUID tenantId, UUID locationId) {
		if (locationId == null) {
			return null;
		}
		return paymentLocationRepository.findByIdAndTenantId(locationId, tenantId)
				.map(TenantPaymentLocationEntity::getName).orElse(null);
	}

	private String resolveBankName(UUID tenantId, UUID bankTemplateId) {
		if (bankTemplateId == null) {
			return null;
		}
		return bankTemplateRepository.findByIdAndTenantId(bankTemplateId, tenantId)
				.map(TenantBankTemplateEntity::getBankName).orElse(null);
	}

	private TenantEmployeeEntity requireEmployee(UUID tenantId, UUID employeeId) {
		return employeeRepository.findByIdAndTenantId(employeeId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
	}

	private TenantCompanyEntity requireCompany(UUID tenantId, UUID companyId) {
		return companyRepository.findByIdAndTenantId(companyId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
	}

	private String normalizeChannel(String value) {
		if (value == null || value.isBlank()) {
			throw badRequest("channelType is required");
		}
		String n = value.trim().toUpperCase(Locale.ROOT);
		if (!CHANNEL_TYPES.contains(n)) {
			throw badRequest("channelType must be BANK or CASH");
		}
		return n;
	}

	private String normalizeSplit(String value) {
		if (value == null || value.isBlank()) {
			throw badRequest("splitType is required");
		}
		String n = value.trim().toUpperCase(Locale.ROOT);
		if (!SPLIT_TYPES.contains(n)) {
			throw badRequest("splitType must be PERCENT or AMOUNT");
		}
		return n;
	}

	private String normalizeCurrency(String value) {
		if (value == null || value.isBlank()) {
			throw badRequest("currency is required");
		}
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private String trim(String value) {
		return value != null ? value.trim() : null;
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}
