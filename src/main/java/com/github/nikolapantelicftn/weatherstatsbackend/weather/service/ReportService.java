package com.github.nikolapantelicftn.weatherstatsbackend.weather.service;

import com.github.nikolapantelicftn.weatherstatsbackend.city.model.City;
import com.github.nikolapantelicftn.weatherstatsbackend.city.service.CityService;
import com.github.nikolapantelicftn.weatherstatsbackend.exceptions.EntityExistsException;
import com.github.nikolapantelicftn.weatherstatsbackend.http.client.WeatherClient;
import com.github.nikolapantelicftn.weatherstatsbackend.weather.model.DayReport;
import com.github.nikolapantelicftn.weatherstatsbackend.weather.repository.DayReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final DayReportRepository repository;
    private final CityService cityService;
    private final WeatherClient client;

    public ReportService(DayReportRepository repository, CityService cityService, WeatherClient client) {
        this.repository = repository;
        this.cityService = cityService;
        this.client = client;
    }

    @Transactional
    @PostConstruct
    public void initReports() {
        log.info("Initializing weather reports.");

        cityService.get().forEach(this::saveCityDayReports);

        log.info("Finished initializing weather reports.");
    }

    private void saveCityDayReports(City city) {
        List<DayReport> dayReports = client.getForCity(city.getName());
        clearCityReports(city);
        dayReports.forEach(report -> {
            report.setCity(city);
            create(report);
        });
    }

    private void clearCityReports(City city) {
        List<Long> idsToClear = city.getDayReports().stream()
                .map(DayReport::getId).collect(Collectors.toList());
        city.clearDayReports();
        deleteAll(idsToClear);
        log.info("Cleared previous day reports for city '{}'", city.getName());
    }

    @Transactional(readOnly = true)
    public List<DayReport> get() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DayReport> get(List<LocalDate> dates) {
        Objects.requireNonNull(dates);
        log.info("Fetching reports by dates.");

        return repository.findByDateIn(dates);
    }

    @Transactional(readOnly = true)
    public List<DayReport> get(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        log.info("Fetching all city weather reports from {} to {}", from, to);

        List<LocalDate> dates = getDatesFromInterval(from, to);
        return get(dates);
    }

    private List<LocalDate> getDatesFromInterval(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = LocalDate.from(from);
        while (!current.isAfter(to)) {
            dates.add(LocalDate.from(current));
            current = current.plus(Period.ofDays(1));
        }
        return dates;
    }

    @Transactional
    public DayReport create(DayReport newReport) {
        Objects.requireNonNull(newReport);
        if (newReport.getId() != null && repository.existsById(newReport.getId())) {
            throw new EntityExistsException("Day already report exists.");
        }
        return repository.save(newReport);
    }

    @Transactional
    public void deleteAll(List<Long> ids) {
        repository.deleteAllById(ids);
    }

}
