package com.isp.sitesurvey.controller;

import com.isp.sitesurvey.dto.request.RfScanRequest;
import com.isp.sitesurvey.entity.*;
import com.isp.sitesurvey.exception.ResourceNotFoundException;
import com.isp.sitesurvey.repository.*;
import com.isp.sitesurvey.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rf-scans")
@RequiredArgsConstructor
public class RfScanController {

    private final RfScanRepository rfScanRepository;
    private final PropertyRepository propertyRepository;
    private final FloorRepository floorRepository;
    private final FileEntityRepository fileEntityRepository;
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody RfScanRequest req) {
        Property property = propertyRepository.findById(req.propertyId())
            .orElseThrow(() -> new ResourceNotFoundException("Property", req.propertyId()));
        Floor floor = req.floorId() != null
            ? floorRepository.findById(req.floorId()).orElse(null) : null;
        RfScan scan = RfScan.builder()
            .property(property).floor(floor)
            .tool(req.tool() != null ? req.tool() : com.isp.sitesurvey.enums.RfTool.MANUAL)
            .parsedJson(req.parsedJson())
            .notes(req.notes())
            .build();
        RfScan saved = rfScanRepository.save(scan);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMap(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        RfScan scan = rfScanRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("RfScan", id));
        rfScanRepository.delete(scan);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam Long propertyId,
            @RequestParam(required = false) Long floorId) {
        List<RfScan> scans = floorId != null
            ? rfScanRepository.findByPropertyIdAndFloorId(propertyId, floorId)
            : rfScanRepository.findByPropertyId(propertyId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RfScan s : scans) result.add(toMap(s));
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toMap(RfScan s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("tool", s.getTool() != null ? s.getTool().toString() : "MANUAL");
        m.put("propertyId", s.getProperty() != null ? s.getProperty().getId() : null);
        m.put("floorId", s.getFloor() != null ? s.getFloor().getId() : null);
        m.put("floorLabel", s.getFloor() != null ? s.getFloor().getLevelLabel() : null);
        m.put("notes", s.getNotes() != null ? s.getNotes() : "");
        m.put("parsedJson", s.getParsedJson() != null ? s.getParsedJson() : "");
        m.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "");
        return m;
    }
}