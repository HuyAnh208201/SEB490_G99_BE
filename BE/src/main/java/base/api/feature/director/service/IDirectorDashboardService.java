package base.api.feature.director.service;

import base.api.feature.director.dto.response.DirectorDashboardResponse;

import java.time.LocalDate;

public interface IDirectorDashboardService {

    DirectorDashboardResponse getDashboard(LocalDate from, LocalDate to);
}
