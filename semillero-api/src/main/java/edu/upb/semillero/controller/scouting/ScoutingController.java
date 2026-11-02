package edu.upb.semillero.controller.scouting;

import edu.upb.semillero.scouting.ScoutingContactService;
import edu.upb.semillero.scouting.ScoutingProspectService;
import edu.upb.semillero.scouting.ScoutingWorkflowService;
import edu.upb.semillero.scouting.ScoutingOidcIdentityService;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scouting")
public class ScoutingController {
    private final ScoutingProspectService prospects;
    private final ScoutingWorkflowService workflow;
    private final ScoutingContactService contacts;
    private final ScoutingOidcIdentityService identities;

    public ScoutingController(ScoutingProspectService prospects, ScoutingWorkflowService workflow,
            ScoutingContactService contacts, ScoutingOidcIdentityService identities) {
        this.prospects = prospects; this.workflow = workflow; this.contacts = contacts; this.identities = identities;
    }

    @GetMapping("/prospects")
    public Map<String,Object> listProspects(@RequestParam(required=false) String stage,
            @RequestParam(required=false) String category, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return prospects.listProspects(stage, category, page, size);
    }

    @GetMapping("/prospects/duplicates")
    public List<Map<String,Object>> duplicates(@RequestParam String firstName, @RequestParam String lastName,
            @RequestParam String birthDate) {
        return prospects.duplicateCandidates(firstName, lastName, birthDate);
    }

    @PostMapping("/prospects")
    public ResponseEntity<Map<String,Object>> createProspect(@RequestBody Map<String,Object> request) {
        Map<String,Object> response = prospects.createProspect(request);
        return ResponseEntity.created(URI.create("/api/v1/scouting/prospects/" + response.get("id"))).body(response);
    }

    @GetMapping("/prospects/{id}")
    public Map<String,Object> getProspect(@PathVariable UUID id) { return prospects.getProspect(id); }

    @PostMapping("/prospects/{id}/duplicate-review")
    public Map<String,Object> reviewDuplicate(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return prospects.reviewDuplicate(id, request);
    }

    @GetMapping("/assignments")
    public List<Map<String,Object>> assignments(@RequestParam(required=false) String prospectId) {
        return prospects.assignments(prospectId);
    }

    @GetMapping("/assignments/mine")
    public List<Map<String,Object>> myAssignments() { return prospects.assignmentsMine(); }

    @PostMapping("/assignments")
    public ResponseEntity<List<Map<String,Object>>> assign(@RequestBody Map<String,Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prospects.assign(request));
    }

    @GetMapping("/criteria-sets")
    public List<Map<String,Object>> criteriaSets(@RequestParam(required=false) String category) {
        return prospects.criteriaSets(category);
    }

    @PostMapping("/criteria-sets")
    public ResponseEntity<Map<String,Object>> createCriteriaSet(@RequestBody Map<String,Object> request) {
        Map<String,Object> response = prospects.createCriteriaSet(request);
        return ResponseEntity.created(URI.create("/api/v1/scouting/criteria-sets/" + response.get("id"))).body(response);
    }

    @PostMapping("/criteria-sets/{id}/publish")
    public Map<String,Object> publishCriteria(@PathVariable UUID id) { return prospects.publishCriteria(id); }

    @PostMapping("/observations/reports")
    public ResponseEntity<Map<String,Object>> createReport(@RequestBody Map<String,Object> request,
            @RequestHeader(name="Idempotency-Key", required=false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prospects.createReport(request, idempotencyKey));
    }

    @GetMapping("/prospects/{id}/history")
    public Map<String,Object> history(@PathVariable UUID id) { return prospects.history(id); }

    @GetMapping("/prospects/{id}/context")
    public Map<String,Object> context(@PathVariable UUID id) { return prospects.context(id); }

    @PostMapping("/prospects/{id}/anonymize")
    public Map<String,Object> anonymize(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return prospects.anonymize(id, (String) request.get("reason"));
    }

    @PostMapping("/exports")
    public ResponseEntity<Map<String,Object>> createExport(@RequestBody Map<String,Object> request) {
        Map<String,Object> response = prospects.createExport(request);
        return ResponseEntity.created(URI.create("/api/v1/scouting/exports/" + response.get("id"))).body(response);
    }

    @GetMapping("/exports/{id}")
    public Map<String,Object> downloadExport(@PathVariable UUID id) { return prospects.downloadExport(id); }

    @PostMapping("/prospects/{id}/stage-transitions")
    public Map<String,Object> transition(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return workflow.requestStage(id, request);
    }

    @PostMapping("/prospects/{id}/stage-approval")
    public Map<String,Object> approveStage(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return workflow.approveStage(id, request);
    }

    @PostMapping("/prospects/{id}/consents")
    public ResponseEntity<Map<String,Object>> consent(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contacts.grantConsent(id, request));
    }

    @GetMapping("/prospects/{id}/consents")
    public List<Map<String,Object>> consents(@PathVariable UUID id) { return contacts.consents(id); }

    @PostMapping("/prospects/{id}/consents/{consentId}/revoke")
    public Map<String,Object> revokeConsent(@PathVariable UUID id, @PathVariable UUID consentId,
            @RequestBody Map<String,Object> request) {
        return contacts.revokeConsent(id, consentId, request);
    }

    @PostMapping("/prospects/{id}/communications")
    public ResponseEntity<Map<String,Object>> communicate(@PathVariable UUID id, @RequestBody Map<String,Object> request,
            @RequestHeader(name="Idempotency-Key", required=false) String idempotencyKey) {
        return ResponseEntity.accepted().body(contacts.createCommunication(id, request, idempotencyKey));
    }

    @GetMapping("/prospects/{id}/communications")
    public List<Map<String,Object>> communications(@PathVariable UUID id) { return contacts.communications(id); }

    @GetMapping("/message-templates")
    public List<Map<String,Object>> messageTemplates() { return contacts.templates(); }

    @PostMapping("/message-templates")
    public ResponseEntity<Map<String,Object>> createTemplate(@RequestBody Map<String,Object> request) {
        Map<String,Object> response = contacts.createTemplate(request);
        return ResponseEntity.created(URI.create("/api/v1/scouting/message-templates/" + response.get("id"))).body(response);
    }

    @PostMapping("/message-templates/{id}/approve")
    public Map<String,Object> approveTemplate(@PathVariable UUID id) { return contacts.approveTemplate(id); }

    @PostMapping("/communications/{id}/retry")
    public Map<String,Object> retryCommunication(@PathVariable UUID id) { return contacts.retryCommunication(id); }

    @PostMapping("/trials")
    public ResponseEntity<List<Map<String,Object>>> createTrial(@RequestBody Map<String,Object> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflow.createTrials(request));
    }

    @PutMapping("/trials/{id}/result")
    public Map<String,Object> completeTrial(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return workflow.completeTrial(id, request);
    }

    @PostMapping("/prospects/{id}/link-player")
    public Map<String,Object> linkPlayer(@PathVariable UUID id, @RequestBody Map<String,Object> request) {
        return workflow.linkPlayer(id, request);
    }

    @PostMapping("/scopes")
    public List<Map<String,Object>> grantScope(@RequestBody Map<String,Object> request) { return workflow.grants(request); }

    @PutMapping("/scopes/{id}/revoke")
    public Map<String,Object> revokeScope(@PathVariable long id) { return workflow.revokeScope(id); }

    @GetMapping("/audit-events")
    public List<Map<String,Object>> auditEvents(@RequestParam(required=false) String resourceType,
            @RequestParam(required=false) String resourceId, @RequestParam(defaultValue="50") int limit) {
        return workflow.auditEvents(resourceType, resourceId, limit);
    }

    @GetMapping("/outbox")
    public List<Map<String,Object>> outbox(@RequestParam(defaultValue="50") int limit) { return workflow.outbox(limit); }

    @PostMapping("/outbox/{id}/retry")
    public Map<String,Object> retryOutbox(@PathVariable UUID id) { return workflow.retryOutbox(id); }

    @GetMapping("/security-alerts")
    public List<Map<String,Object>> securityAlerts(@RequestParam(defaultValue="50") int limit) {
        return workflow.securityAlerts(limit);
    }

    @PostMapping("/security-alerts/{id}/acknowledge")
    public Map<String,Object> acknowledgeAlert(@PathVariable long id) {
        return workflow.acknowledgeAlert(id);
    }

    @PostMapping("/config/oidc-identities")
    public ResponseEntity<Map<String,Object>> createOidcIdentity(@RequestBody Map<String,Object> request) {
        Map<String,Object> response = identities.create(request);
        return ResponseEntity.created(URI.create("/api/v1/scouting/config/oidc-identities/" + response.get("id"))).body(response);
    }

    @GetMapping("/config/oidc-identities")
    public List<Map<String,Object>> oidcIdentities() { return identities.list(); }

    @PutMapping("/config/oidc-identities/{id}/deactivate")
    public Map<String,Object> deactivateOidcIdentity(@PathVariable long id) { return identities.deactivate(id); }
}
