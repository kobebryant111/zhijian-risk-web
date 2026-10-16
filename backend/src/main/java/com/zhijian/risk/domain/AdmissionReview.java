package com.zhijian.risk.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("admission_reviews")
public class AdmissionReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orgId;
    private String publicId;
    private String partnerName;
    private String partnerType = "loan";
    private String scenario = "准入";
    private String conclusion = "待核验";
    private String itemsJson = "[]";
    private String remediationJson = "[]";
    private String summary = "";
    private String markdown = "";
    private boolean evidenceIncomplete = true;
    private String evidenceSources = "";
    private String aiNotes = "";
    private String status = "done";
    private int progress = 100;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public Long getOrgId(){return orgId;} public void setOrgId(Long orgId){this.orgId=orgId;}
    public String getPublicId(){return publicId;} public void setPublicId(String publicId){this.publicId=publicId;}
    public String getPartnerName(){return partnerName;} public void setPartnerName(String partnerName){this.partnerName=partnerName;}
    public String getPartnerType(){return partnerType;} public void setPartnerType(String partnerType){this.partnerType=partnerType;}
    public String getScenario(){return scenario;} public void setScenario(String scenario){this.scenario=scenario;}
    public String getConclusion(){return conclusion;} public void setConclusion(String conclusion){this.conclusion=conclusion;}
    public String getItemsJson(){return itemsJson;} public void setItemsJson(String itemsJson){this.itemsJson=itemsJson;}
    public String getRemediationJson(){return remediationJson;} public void setRemediationJson(String remediationJson){this.remediationJson=remediationJson;}
    public String getSummary(){return summary;} public void setSummary(String summary){this.summary=summary;}
    public String getMarkdown(){return markdown;} public void setMarkdown(String markdown){this.markdown=markdown;}
    public boolean isEvidenceIncomplete(){return evidenceIncomplete;} public void setEvidenceIncomplete(boolean evidenceIncomplete){this.evidenceIncomplete=evidenceIncomplete;}
    public String getEvidenceSources(){return evidenceSources;} public void setEvidenceSources(String evidenceSources){this.evidenceSources=evidenceSources;}
    public String getAiNotes(){return aiNotes;} public void setAiNotes(String aiNotes){this.aiNotes=aiNotes;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public int getProgress(){return progress;} public void setProgress(int progress){this.progress=progress;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime updatedAt){this.updatedAt=updatedAt;}
}
