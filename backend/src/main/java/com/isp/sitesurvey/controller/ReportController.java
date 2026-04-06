package com.isp.sitesurvey.controller;

import com.isp.sitesurvey.entity.*;
import com.isp.sitesurvey.enums.ReportStatus;
import com.isp.sitesurvey.exception.ResourceNotFoundException;
import com.isp.sitesurvey.repository.*;
import com.isp.sitesurvey.util.SecurityUtils;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportRepository reportRepository;
    private final PropertyRepository propertyRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final SpaceRepository spaceRepository;
    private final SecurityUtils securityUtils;

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long propertyId = Long.parseLong(body.get("propertyId").toString());
        Property property = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
        User user = securityUtils.getCurrentUser();

        List<Building> buildings = buildingRepository.findByPropertyId(property.getId());

        Report report = Report.builder()
            .property(property).requestedBy(user)
            .status(ReportStatus.GENERATING)
            .build();
        Report saved = reportRepository.save(report);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD, BaseColor.DARK_GRAY);
            Font headingFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, BaseColor.DARK_GRAY);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.BLACK);
            Font smallFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.GRAY);

            Paragraph title = new Paragraph("ISP Site Survey Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph subtitle = new Paragraph("Generated: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")), smallFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            document.add(new Paragraph("Property Details", headingFont));
            document.add(new Paragraph("Name: " + property.getName(), normalFont));
            if (property.getAddressLine1() != null)
                document.add(new Paragraph("Address: " + property.getAddressLine1(), normalFont));
            if (property.getCity() != null)
                document.add(new Paragraph("City: " + property.getCity() + ", " + property.getState(), normalFont));
            if (property.getCountry() != null)
                document.add(new Paragraph("Country: " + property.getCountry(), normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Buildings (" + buildings.size() + ")", headingFont));
            for (Building b : buildings) {
                document.add(new Paragraph("  - " + b.getName() +
                    (b.getCode() != null ? " [" + b.getCode() + "]" : "") +
                    " - " + b.getFloorsCount() + " floor(s)", normalFont));
                List<Floor> floors = floorRepository.findByBuildingId(b.getId());
                for (Floor f : floors) {
                    document.add(new Paragraph("      Floor: " + f.getLevelLabel(), smallFont));
                    List<Space> spaces = spaceRepository.findByFloorId(f.getId());
                    for (Space s : spaces) {
                        document.add(new Paragraph("          Space: " + s.getName() +
                            " (" + s.getType() + ")", smallFont));
                    }
                }
            }

            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("--- End of Report ---", smallFont));
            document.close();

            String base64Pdf = Base64.getEncoder().encodeToString(baos.toByteArray());
            saved.setParameters("{\"pdf\":\"" + base64Pdf + "\"}");
            saved.setStatus(ReportStatus.DONE);
            reportRepository.save(saved);

        } catch (Exception e) {
            saved.setStatus(ReportStatus.FAILED);
            reportRepository.save(saved);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("status", saved.getStatus().toString());
        m.put("propertyId", propertyId);
        
        m.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : "");
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listByProperty(@RequestParam Long propertyId) {
        List<Report> reports = reportRepository.findByPropertyId(propertyId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Report r : reports) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("status", r.getStatus().toString());
            m.put("propertyId", propertyId);
            m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        Report r = reportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Report", id));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("status", r.getStatus().toString());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return ResponseEntity.ok(m);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        Report report = reportRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Report", id));
        try {
            if (report.getParameters() == null || report.getParameters().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            String json = report.getParameters();
            String base64 = json.replaceAll("\\{\"pdf\":\\s*\"", "").replaceAll("\"\\s*\\}", "").trim();
            byte[] pdfBytes = Base64.getDecoder().decode(base64);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                "site-survey-report-" + id + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}