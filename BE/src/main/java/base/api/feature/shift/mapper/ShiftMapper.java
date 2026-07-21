package base.api.feature.shift.mapper;

import base.api.feature.shift.dto.response.AssignedEmployeeResponse;
import base.api.feature.shift.dto.response.AvailableEmployeeResponse;
import base.api.feature.shift.dto.response.ShiftResponse;
import base.api.shared.entity.ShiftAssignmentModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShiftMapper {

    public ShiftResponse toResponse(ShiftModel shift, List<ShiftAssignmentModel> assignments) {
        ShiftResponse response = toListResponse(shift);
        response.setAssignedEmployees(assignments.stream()
                .map(this::toAssignedEmployeeResponse)
                .toList());
        return response;
    }

    public ShiftResponse toListResponse(ShiftModel shift) {
        ShiftResponse response = new ShiftResponse();
        response.setId(shift.getId());
        response.setBranchId(shift.getBranchId());
        response.setCreatedBy(shift.getCreatedBy());
        response.setStartTime(shift.getStartTime());
        response.setEndTime(shift.getEndTime());
        response.setOpeningCash(shift.getOpeningCash());
        response.setExpectedCash(shift.getExpectedCash());
        response.setActualCash(shift.getActualCash());
        response.setDifference(shift.getDifference());
        response.setStatus(shift.getStatus());
        response.setApprovedBy(shift.getApprovedBy());
        response.setClosedBy(shift.getClosedBy());
        response.setStaffNote(shift.getStaffNote());
        response.setReviewNote(shift.getReviewNote());
        response.setCreatedAt(shift.getCreatedAt());
        return response;
    }

    public AssignedEmployeeResponse toAssignedEmployeeResponse(ShiftAssignmentModel assignment) {
        UserModel staff = assignment.getStaff();

        AssignedEmployeeResponse response = new AssignedEmployeeResponse();
        response.setAssignmentId(assignment.getId());
        response.setEmployeeId(staff.getId());
        response.setFullName(staff.getFullName());
        response.setEmail(staff.getEmail());
        // Prefer the role the employee was assigned to fill; fall back to account role.
        response.setRole(assignment.getAssignedRole() != null
                ? assignment.getAssignedRole().toWebRole()
                : (staff.getRole() == null ? null : staff.getRole().toWebRole()));
        response.setCheckInAt(assignment.getCheckInAt());
        response.setCheckOutAt(assignment.getCheckOutAt());
        return response;
    }

    public AvailableEmployeeResponse toAvailableEmployeeResponse(UserModel employee) {
        AvailableEmployeeResponse response = new AvailableEmployeeResponse();
        response.setEmployeeId(employee.getId());
        response.setFullName(employee.getFullName());
        response.setEmail(employee.getEmail());
        response.setBranchId(employee.getBranchId());
        response.setRole(employee.getRole());
        return response;
    }
}
