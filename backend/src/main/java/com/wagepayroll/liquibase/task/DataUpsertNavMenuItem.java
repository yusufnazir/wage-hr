package com.wagepayroll.liquibase.task;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Upserts one row in the {@code nav_menu_item} table.
 * {@code requiredPrivilegeCode} and {@code requiredPlanFeatureCode} are optional — omit or leave blank for null.
 *
 * <pre>{@code
 * <customChange class="com.wagepayroll.liquibase.task.DataUpsertNavMenuItem">
 *     <param name="id"                    value="50000000-0000-0000-0000-000000000001"/>
 *     <param name="path"                  value="/app"/>
 *     <param name="labelKey"              value="nav.dashboard"/>
 *     <param name="sortOrder"             value="0"/>
 *     <param name="requiredPrivilegeCode" value=""/>
 * </customChange>
 * }</pre>
 */
public class DataUpsertNavMenuItem extends CustomDataTaskChange {

	private String id;
	private String path;
	private String labelKey;
	private String sortOrder;
	private String requiredPrivilegeCode;
	private String requiredPlanFeatureCode;

	@Override
	public void handleUpdate() throws Exception {
		String privCode = (requiredPrivilegeCode == null || requiredPrivilegeCode.isBlank()) ? null : requiredPrivilegeCode;
		String planCode = (requiredPlanFeatureCode == null || requiredPlanFeatureCode.isBlank()) ? null : requiredPlanFeatureCode;
		int order = Integer.parseInt(sortOrder);

		try (PreparedStatement check = connection.prepareStatement(
				"SELECT COUNT(*) FROM nav_menu_item WHERE id = ?")) {
			setData(check, 1, id);
			try (ResultSet rs = check.executeQuery()) {
				rs.next();
				if (rs.getInt(1) > 0) {
					try (PreparedStatement ps = connection.prepareStatement(
							"UPDATE nav_menu_item SET path = ?, label_key = ?, sort_order = ?, required_privilege_code = ?, required_plan_feature_code = ?, updated_at = ? WHERE id = ?")) {
						setData(ps, 1, path);
						setData(ps, 2, labelKey);
						ps.setInt(3, order);
						ps.setObject(4, privCode);
						ps.setObject(5, planCode);
						setData(ps, 6, ts);
						setData(ps, 7, id);
						ps.executeUpdate();
					}
					return;
				}
			}
		}
		try (PreparedStatement ps = connection.prepareStatement(
				"INSERT INTO nav_menu_item (id, parent_id, path, label_key, sort_order, required_privilege_code, required_plan_feature_code, created_at, updated_at) VALUES (?,NULL,?,?,?,?,?,?,?)")) {
			setData(ps, 1, id);
			setData(ps, 2, path);
			setData(ps, 3, labelKey);
			ps.setInt(4, order);
			ps.setObject(5, privCode);
			ps.setObject(6, planCode);
			setData(ps, 7, ts);
			setData(ps, 8, ts);
			ps.executeUpdate();
		}
	}

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getPath() { return path; }
	public void setPath(String path) { this.path = path; }

	public String getLabelKey() { return labelKey; }
	public void setLabelKey(String labelKey) { this.labelKey = labelKey; }

	public String getSortOrder() { return sortOrder; }
	public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }

	public String getRequiredPrivilegeCode() { return requiredPrivilegeCode; }
	public void setRequiredPrivilegeCode(String requiredPrivilegeCode) { this.requiredPrivilegeCode = requiredPrivilegeCode; }

	public String getRequiredPlanFeatureCode() { return requiredPlanFeatureCode; }
	public void setRequiredPlanFeatureCode(String requiredPlanFeatureCode) { this.requiredPlanFeatureCode = requiredPlanFeatureCode; }
}
