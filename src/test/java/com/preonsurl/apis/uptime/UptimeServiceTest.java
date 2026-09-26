package com.preonsurl.apis.uptime;

import com.preonsurl.apis.uptime.dto.CreateIncidentRequest;
import com.preonsurl.apis.uptime.dto.IncidentDto;
import com.preonsurl.apis.uptime.dto.UptimeDashboardDto;
import com.preonsurl.apis.uptime.entity.DeploymentRecord;
import com.preonsurl.apis.uptime.entity.IncidentRecord;
import com.preonsurl.apis.uptime.entity.UptimeRecord;
import com.preonsurl.apis.uptime.repository.DeploymentRepository;
import com.preonsurl.apis.uptime.repository.IncidentRepository;
import com.preonsurl.apis.uptime.repository.UptimeRepository;
import com.preonsurl.apis.uptime.service.UptimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UptimeServiceTest {

    @Mock
    private UptimeRepository uptimeRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private IncidentRepository incidentRepository;

    private UptimeService uptimeService;

    @BeforeEach
    void setUp() {
        uptimeService = new UptimeService(uptimeRepository, deploymentRepository, incidentRepository);
        ReflectionTestUtils.setField(uptimeService, "serverPort", 8081);
        ReflectionTestUtils.setField(uptimeService, "configuredCommitRef", "test1234");
        ReflectionTestUtils.setField(uptimeService, "appVersion", "v1.8.4");
        ReflectionTestUtils.setField(uptimeService, "environment", "Production");
    }

    @Test
    void monitoredServicesMap_contains3ConfiguredServicesWithHealthCheckUrls() {
        Map<String, UptimeService.MonitoredServiceConfig> map = uptimeService.getMonitoredServices();
        assertEquals(3, map.size());

        assertTrue(map.containsKey("PUBLIC SECURE LINKS SERVER"));
        assertEquals("PUBLIC_SECURE_LINKS_SERVER", map.get("PUBLIC SECURE LINKS SERVER").type());
        assertEquals("http://127.0.0.1:8081/api/public-links/h", map.get("PUBLIC SECURE LINKS SERVER").url());

        assertTrue(map.containsKey("PRIVATE SECURE LINKS SERVER"));
        assertEquals("PRIVATE_SECURE_LINKS_SERVER", map.get("PRIVATE SECURE LINKS SERVER").type());
        assertEquals("http://127.0.0.1:8081/api/public-links/h", map.get("PRIVATE SECURE LINKS SERVER").url());

        assertTrue(map.containsKey("CONSOLE"));
        assertEquals("CONSOLE", map.get("CONSOLE").type());
        assertEquals("http://127.0.0.1:8081/api/public-links/h", map.get("CONSOLE").url());
    }

    @Test
    void checkAndRecordDeploymentOnStartup_whenNoPreviousDeployment_savesNewEntry() {
        when(deploymentRepository.findTopByOrderByDeployedAtDesc()).thenReturn(Optional.empty());
        when(deploymentRepository.save(any(DeploymentRecord.class))).thenAnswer(i -> i.getArgument(0));

        uptimeService.checkAndRecordDeploymentOnStartup();

        ArgumentCaptor<DeploymentRecord> captor = ArgumentCaptor.forClass(DeploymentRecord.class);
        verify(deploymentRepository).save(captor.capture());
        DeploymentRecord saved = captor.getValue();
        assertEquals("test1234", saved.getCommitRef());
        assertEquals("v1.8.4", saved.getVersion());
        assertEquals("Production", saved.getEnvironment());
    }

    @Test
    void checkAndRecordDeploymentOnStartup_whenSameCommitRef_doesNotDuplicate() {
        DeploymentRecord existing = new DeploymentRecord("test1234", "v1.8.4", "Production", "Successful", "Summary");
        when(deploymentRepository.findTopByOrderByDeployedAtDesc()).thenReturn(Optional.of(existing));

        uptimeService.checkAndRecordDeploymentOnStartup();

        verify(deploymentRepository, never()).save(any(DeploymentRecord.class));
    }

    @Test
    void checkAndRecordDeploymentOnStartup_whenDifferentCommitRef_savesNewEntry() {
        DeploymentRecord previous = new DeploymentRecord("oldcommit", "v1.8.3", "Production", "Successful", "Summary");
        when(deploymentRepository.findTopByOrderByDeployedAtDesc()).thenReturn(Optional.of(previous));
        when(deploymentRepository.save(any(DeploymentRecord.class))).thenAnswer(i -> i.getArgument(0));

        uptimeService.checkAndRecordDeploymentOnStartup();

        ArgumentCaptor<DeploymentRecord> captor = ArgumentCaptor.forClass(DeploymentRecord.class);
        verify(deploymentRepository).save(captor.capture());
        assertEquals("test1234", captor.getValue().getCommitRef());
    }

    @Test
    void resolveCommitRef_returnsConfiguredOrFallback() {
        String ref = uptimeService.resolveCommitRef();
        assertNotNull(ref);
        assertEquals("test1234", ref);
    }

    @Test
    void createAndResolveIncident_worksAsExpected() {
        CreateIncidentRequest req = new CreateIncidentRequest(
                "DB connection timeout",
                "Transient network spike",
                "Investigating",
                "Major",
                "Database"
        );

        IncidentRecord mockSaved = new IncidentRecord(
                req.title(), req.description(), req.status(), req.severity(), req.impactedService(), Instant.now(), null
        );
        mockSaved.setId(101L);

        when(incidentRepository.save(any(IncidentRecord.class))).thenReturn(mockSaved);

        IncidentDto created = uptimeService.createIncident(req);
        assertNotNull(created);
        assertEquals("DB connection timeout", created.title());
        assertEquals("Investigating", created.status());

        when(incidentRepository.findById(101L)).thenReturn(Optional.of(mockSaved));
        when(incidentRepository.save(any(IncidentRecord.class))).thenReturn(mockSaved);

        IncidentDto resolved = uptimeService.resolveIncident(101L);
        assertNotNull(resolved);
        assertEquals("Resolved", mockSaved.getStatus());
        assertNotNull(mockSaved.getResolvedAt());
    }

    @Test
    void getDashboardData_cachesResponseFor1HourAndRepresents3Services() {
        when(incidentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(incidentRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(deploymentRepository.findTopByOrderByDeployedAtDesc()).thenReturn(Optional.of(
                new DeploymentRecord("test1234", "v1.8.4", "Production", "Successful", "Production release")
        ));
        when(uptimeRepository.findByRecordedAtAfterOrderByRecordedAtAsc(any())).thenReturn(List.of());

        // First call populates cache
        UptimeDashboardDto first = uptimeService.getDashboardData();
        assertNotNull(first);
        assertEquals("operational", first.overallStatus());
        assertEquals("99.99%", first.currentSla());
        assertEquals(90, first.history90Days().size());

        // Must represent exactly the 3 configured services on the UI
        assertEquals(3, first.services().size());
        assertEquals("PUBLIC SECURE LINKS SERVER", first.services().get(0).name());
        assertEquals("PRIVATE SECURE LINKS SERVER", first.services().get(1).name());
        assertEquals("CONSOLE", first.services().get(2).name());

        assertTrue(first.incidents().isEmpty(), "No seeded incidents should exist");

        // Second call should return from cache without re-querying deployment
        UptimeDashboardDto second = uptimeService.getDashboardData();
        assertEquals(first.overallTitle(), second.overallTitle());
        assertEquals(first.latestDeployment().commitRef(), second.latestDeployment().commitRef());
        verify(deploymentRepository, times(1)).findTopByOrderByDeployedAtDesc();
    }
}
