package com.itss.vib.t24.l3payments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.itss.transact.l3.GenericApi;
import com.itss.transact.l3.ItssParameterApis;
import com.temenos.api.LocalRefGroup;
import com.temenos.api.TField;
import com.temenos.api.TStructure;
import com.temenos.api.TValidationResponse;
import com.temenos.api.exceptions.T24CoreException;
import com.temenos.t24.api.complex.eb.templatehook.TransactionContext;
import com.temenos.t24.api.hook.system.RecordLifecycle;
import com.temenos.t24.api.records.company.CompanyRecord;
import com.temenos.t24.api.records.ebvibdivisionpurposecode.EbVibDivisionPurposeCodeRecord;
import com.temenos.t24.api.records.ebvibdivisionpurposecode.PurposeCodeClass;
import com.temenos.t24.api.records.mnemoniccompany.MnemonicCompanyRecord;
import com.temenos.t24.api.records.pporderentry.PpOrderEntryRecord;
import com.temenos.t24.api.system.DataAccess;

/**
 * TODO: Document me!
 *
 * @author Arulmurugan R
 *
 */
public class VibPurposeCodeValidate extends RecordLifecycle {

    @Override
    public TValidationResponse validateRecord(String application, String currentRecordId, TStructure currentRecord,
            TStructure unauthorisedRecord, TStructure liveRecord, TransactionContext transactionContext) {
        DataAccess da = new DataAccess(this); // Init data access
        GenericApi genApi = new GenericApi(); // Init API helper
        PpOrderEntryRecord ppOrderEntryRecord = new PpOrderEntryRecord(currentRecord); // Wrap record
        setHighLevelBranch(ppOrderEntryRecord);
        List<LocalRefGroup> invoiceInfoClasses = ppOrderEntryRecord.getLocalRefGroups("VIB.INV.NUMBER"); // Get invoice
                                                                                                         // // refs
        List<LocalRefGroup> legalDocsInfoClasses = ppOrderEntryRecord.getLocalRefGroups("VIB.LG.ID"); // Get legal doc
                                                                                                      // // refs
        List<LocalRefGroup> purposeCodeGroups = ppOrderEntryRecord.getLocalRefGroups("PURPOSE.CODE"); // Get purpose //
                                                                                                      // codes
        String division = ppOrderEntryRecord.getLocalRefField("VIB.DIVISION").getValue(); // Get division
        Map<String, String> purposeCodeDivisionMap = buildPurposeCodeDivisionMap(purposeCodeGroups, division); // Build
                                                                                                               // // map
        validatePurposeCodeCombinations(purposeCodeGroups, ppOrderEntryRecord); // Validate purpose codes
        String requiredFieldRule = determineRequiredFieldRule(genApi, da, purposeCodeDivisionMap); // Get rules
        String[] parts = requiredFieldRule.split("\\.");
        // Accessing the parts
        String result = parts[0]; // "INV*LEGAL_DOC"
        defaultLimitBase(result, ppOrderEntryRecord);
        String mandatoryFlags = parts[1]; // "M-N-M"
        applyFieldRules(mandatoryFlags, ppOrderEntryRecord, invoiceInfoClasses, legalDocsInfoClasses); // Apply rules
        currentRecord.set(ppOrderEntryRecord.toStructure()); // Update record
        return ppOrderEntryRecord.getValidationResponse(); // Return validation result
    }

    /**
     * @param result
     * @param ppOrderEntryRecord
     */
    private void defaultLimitBase(String results, PpOrderEntryRecord ppOrderEntryRecord) {
        if (results == null || results.isEmpty()) {
            return;
        }

        String[] resultItems = results.split("\\*");
        List<LocalRefGroup> groupList = ppOrderEntryRecord.getLocalRefGroups("VIB.LI.BASE");

        // Handle up to 3 result items only
        for (int i = 0; i < resultItems.length && i < 3; i++) {
            LocalRefGroup group = ppOrderEntryRecord.createLocalRefGroup("VIB.LI.BASE");
            group.getLocalRefField("VIB.LI.BASE").set(resultItems[i].trim());

            if (groupList.size() > i) {
                groupList.set(i, group); // Overwrite existing group at index i
            } else {
                groupList.add(i, group); // Add new group at index i
            }
        }
    }

    private Map<String, String> buildPurposeCodeDivisionMap(List<LocalRefGroup> purposeCodeGroups, String division) {
        Map<String, String> map = new HashMap<>();
        if (purposeCodeGroups != null && !purposeCodeGroups.isEmpty()) {
            // Put the first purpose code with division
            String firstPurposeCode = purposeCodeGroups.get(0).getLocalRefField("PURPOSE.CODE").getValue();
            map.put(firstPurposeCode, division);
        }
        // Also add all purpose codes to list if needed later (optional)
        for (LocalRefGroup group : purposeCodeGroups) {
            String code = group.getLocalRefField("PURPOSE.CODE").getValue();
            map.putIfAbsent(code, division);
        }
        return map;
    }

    // Validate combinations of purpose codes from the provided list of
    // LocalRefGroup
    private void validatePurposeCodeCombinations(List<LocalRefGroup> purposeCodeGroups,
            PpOrderEntryRecord ppOrderEntryRecord) {
        // Return immediately if list is null or has 1 or fewer elements (no validation
        // needed)
        String groupName = "PURPOSE.CODE";
        if (purposeCodeGroups == null || purposeCodeGroups.size() > 1) {
            // Extract the first and second purpose codes from the list
            String first = purposeCodeGroups.get(0).getLocalRefField("PURPOSE.CODE").getValue();
            String second = purposeCodeGroups.get(1).getLocalRefField("PURPOSE.CODE").getValue();
            LocalRefGroup newgrp = ppOrderEntryRecord.createLocalRefGroup("PURPOSE.CODE");
            // Check valid combinations based on specific first purpose code values
            if (first.equals("001S1") && !(second.equals("001S2") || second.equals("001S2.INV"))) {
                // Throw exception if second code is invalid for first code "001S10"
                // EB-PURPOSE.CODE.INVALID
                newgrp.getLocalRefField("PURPOSE.CODE").setError("EB-PURPOSE.CODE.INVALID");
            } else if (first.equals("0014S1") && !(second.equals("004S2") || second.equals("004S2.INV"))) {
                // Throw exception if second code is invalid for first code "0014S1"
                // EB-PURPOSE.CODE.INVALID
                newgrp.getLocalRefField("PURPOSE.CODE").setError("EB-PURPOSE.CODE.INVALID");
            } else {
                // Throw exception if multiple purpose codes exist but do not match any valid
                // combination
                // EB-MULTIPLE.PURPOSE.CODE.NOT.ALLOWED
                // newgrp.getLocalRefField("PURPOSE.CODE").setError("EB-MULTIPLE.PURPOSE.CODE.NOT.ALLOWED");
            }
            ppOrderEntryRecord.getLocalRefGroups(groupName).set(1, newgrp);
        }
    }

    // Determine the required field rule based on the purpose code and division
    private String determineRequiredFieldRule(GenericApi genApi, DataAccess da,
            Map<String, String> purposeCodeDivisionMap) {
        TStructure etdStruct = genApi.itssGetRecord("VibPurposeCodeValidate", "EB.VIB.DIVISION.PURPOSE.CODES", "SYSTEM",
                da);
        EbVibDivisionPurposeCodeRecord etdRec = new EbVibDivisionPurposeCodeRecord(etdStruct);
        ItssParameterApis itssParameterApis = new ItssParameterApis();
        Set<String> uniqueItems = getMatchingItems(etdRec, purposeCodeDivisionMap, itssParameterApis, da);
        String result = buildResultString(uniqueItems);
        String flags = buildMandatoryFlags(uniqueItems);
        return result + "." + flags;
    }

    // Helper to find INV, PPT, LEGAL_DOC based on rules
    private Set<String> getMatchingItems(EbVibDivisionPurposeCodeRecord etdRec, Map<String, String> inputMap,
            ItssParameterApis api, DataAccess da) {
        Set<String> foundItems = new HashSet<>();
        List<String> invList = api.getItssParamValueAsList("PURPOSE.CODE.DIVISION", "INV", da);
        List<String> pptList = api.getItssParamValueAsList("PURPOSE.CODE.DIVISION", "PPT", da);
        List<String> legalList = api.getItssParamValueAsList("PURPOSE.CODE.DIVISION", "LEGAL.DOC", da);
        for (PurposeCodeClass pc : etdRec.getPurposeCode()) {
            String purpose = pc.getPurposeCode().getValue();
            String division = pc.getDivision().getValue();
            String inputDiv = inputMap.get(purpose);
            String combined = purpose + "-" + division;
            if (inputDiv != null && inputDiv.equals(division)) {
                if (invList.contains(combined))
                    foundItems.add("INV");
                if (pptList.contains(combined))
                    foundItems.add("PPT");
                if (legalList.contains(combined))
                    foundItems.add("LEGAL_DOC");
            }
        }
        return foundItems;
    }

    // Helper to build result string like INV*PPT
    private String buildResultString(Set<String> items) {
        List<String> ordered = new ArrayList<>();
        if (items.contains("INV"))
            ordered.add("INV");
        if (items.contains("PPT"))
            ordered.add("PPT");
        if (items.contains("LEGAL_DOC"))
            ordered.add("LEGAL_DOC");
        String result = "";
        for (String item : ordered) {
            if (ordered.contains(item)) {
                if (!result.isEmpty()) {
                    result += "*";
                }
                result += item;
            }
        }
        return result;
    }

    // Helper to build flags like M-N-M
    private String buildMandatoryFlags(Set<String> items) {
        String inv = items.contains("INV") ? "M" : "N";
        String ppt = items.contains("PPT") ? "M" : "N";
        String legal = items.contains("LEGAL_DOC") ? "M" : "N";
        return inv + "-" + ppt + "-" + legal;
    }

    // Apply field validation rules on the order entry and related info groups based
    // on the requiredFieldRule
    private void applyFieldRules(String requiredFieldRule, PpOrderEntryRecord ppOrderEntryRecord,
            List<LocalRefGroup> invoiceInfoClasses, List<LocalRefGroup> legalDocsInfoClasses) {
        // Throw exception if the required field rule is not provided or empty
        if (requiredFieldRule == null || requiredFieldRule.isEmpty()) {
            throw new T24CoreException("Required field rule cannot be determined");
        }
        // Split the rule string into its component parts
        String[] parts = requiredFieldRule.split("-");
        String invoiceInfo = parts[0];
        String passportInfo = parts[1];
        String legalDocInfo = parts[2];
        // Validate the invoice info according to the extracted rule part
        validateInvoiceInfo(invoiceInfoClasses, invoiceInfo, ppOrderEntryRecord);
        // Validate the passport info based on the rule
        validatePassportInfo(ppOrderEntryRecord, passportInfo);
        // Validate the legal documents info as per the rule
        validateLegalDocsInfo(legalDocsInfoClasses, legalDocInfo, ppOrderEntryRecord);
    }

    // Validate invoice information fields based on the invoiceInfo rule (Mandatory
    // 'M' or Not allowed 'N')
    private void validateInvoiceInfo(List<LocalRefGroup> invoiceInfoClasses, String invoiceInfo,
            PpOrderEntryRecord ppOrderEntryRecord) {

        LocalDate transactionDate = VibPaymentHelperClass.parseDate(
                ppOrderEntryRecord.getProcessingdate().getValue(), "Processing Date");

        int i = 0;
        String groupName = "VIB.INV.NUMBER";

        if (invoiceInfoClasses.isEmpty() && "M".equalsIgnoreCase(invoiceInfo)) {
            LocalRefGroup newgrp = ppOrderEntryRecord.createLocalRefGroup(groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.NUMBER", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.STA.DAT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.END.DAT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.CCY", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.TRAN.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.LI.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.LI.CCY", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.INV.LI.CHECK", null, i, newgrp, groupName);
            ppOrderEntryRecord.getLocalRefGroups(groupName).add(i, newgrp);
        } else {
            String paymentCcy = ppOrderEntryRecord.getTransactioncurrency().getValue();

            for (i = 0; i < invoiceInfoClasses.size(); i++) {
                LocalRefGroup invoiceInfoClass = invoiceInfoClasses.get(i);
                LocalRefGroup newgrp = ppOrderEntryRecord.createLocalRefGroup(groupName);

                // Extract field values
                String invNumber = getFieldValueSafely(invoiceInfoClass, "VIB.INV.NUMBER");
                String invStartDate = getFieldValueSafely(invoiceInfoClass, "VIB.INV.STA.DAT");
                String invEndDate = getFieldValueSafely(invoiceInfoClass, "VIB.INV.END.DAT");
                String invAmount = getFieldValueSafely(invoiceInfoClass, "VIB.INV.AMT");
                String invCurrency = getFieldValueSafely(invoiceInfoClass, "VIB.INV.CCY");
                String transferAmount = getFieldValueSafely(invoiceInfoClass, "VIB.INV.TRAN.AMT");
                String liRemainAmount = getFieldValueSafely(invoiceInfoClass, "VIB.INV.LI.AMT");
                String liRemainCurrency = getFieldValueSafely(invoiceInfoClass, "VIB.INV.LI.CCY");
                String liChecking = getFieldValueSafely(invoiceInfoClass, "VIB.INV.LI.CHECK");

                String[] fields = {
                    "VIB.INV.NUMBER", "VIB.INV.STA.DAT", "VIB.INV.END.DAT", "VIB.INV.AMT",
                    "VIB.INV.CCY", "VIB.INV.TRAN.AMT", "VIB.INV.LI.AMT", "VIB.INV.LI.CCY", "VIB.INV.LI.CHECK"
                };

                String[] values = {
                    invNumber, invStartDate, invEndDate, invAmount, invCurrency,
                    transferAmount, liRemainAmount, liRemainCurrency, liChecking
                };

                for (int j = 0; j < fields.length; j++) {
                    String fieldName = fields[j];
                    String fieldValue = values[j];
                    boolean hasError = false;

                    if ("M".equalsIgnoreCase(invoiceInfo)) {
                        hasError = isNullOrEmpty(fieldValue);
                        if (hasError) {
                            setMandatoryFieldError(ppOrderEntryRecord, fieldName, i, newgrp, groupName);
                        }
                    } else if ("N".equalsIgnoreCase(invoiceInfo)) {
                        hasError = !isNullOrEmpty(fieldValue);
                        if (hasError) {
                            setInputNotAllowedError(ppOrderEntryRecord, fieldName, i, newgrp, groupName);
                        }
                    }

                    if (!hasError && "VIB.INV.CCY".equals(fieldName)) {
                        if (!isNullOrEmpty(fieldValue) && !fieldValue.equalsIgnoreCase(paymentCcy)) {
                            List<String> errMsg = new ArrayList<>();
                            errMsg.add("EB-INVALID.CURRENCY");
                            errMsg.add("VIB.INV.CCY");
                            newgrp.getLocalRefField(fieldName).setError(errMsg.toString());
                            hasError = true;
                        }
                    }

                    if (!hasError) {
                        newgrp.getLocalRefField(fieldName).set(fieldValue);
                    }
                }

                // Start Date Validation
                boolean hasStartDateError = false;
                if (!isNullOrEmpty(invStartDate)) {
                    LocalDate startDate = parseDate(invStartDate, "VIB.INV.STA.DAT");
                    if (startDate != null && startDate.isAfter(transactionDate)) {
                        List<String> errMsg = new ArrayList<>();
                        errMsg.add("EB-DATE.AFTER.PROCESSING");
                        errMsg.add("VIB.INV.STA.DAT");
                        newgrp.getLocalRefField("VIB.INV.STA.DAT").setError(errMsg.toString());
                        hasStartDateError = true;
                    }
                    if (!hasStartDateError) {
                        newgrp.getLocalRefField("VIB.INV.STA.DAT").set(invStartDate);
                    }
                }

                // End Date Validation
                boolean hasEndDateError = false;
                if (!isNullOrEmpty(invEndDate)) {
                    LocalDate endDate = parseDate(invEndDate, "VIB.INV.END.DAT");
                    if (endDate != null && endDate.isBefore(transactionDate)) {
                        TField field = newgrp.getLocalRefField("VIB.INV.END.DAT");
                        field.setOverride("VIB.INV.END.DAT is before transaction date. Please confirm to proceed.");
                        hasEndDateError = true;
                    }
                    if (!hasEndDateError) {
                        newgrp.getLocalRefField("VIB.INV.END.DAT").set(invEndDate);
                    }
                }

                // TRAN.AMT <= INV.AMT validation
                boolean hasAmountError = false;
                if (!isNullOrEmpty(invAmount) && !isNullOrEmpty(transferAmount)) {
                    try {
                        BigDecimal baseAmt = new BigDecimal(invAmount.trim());
                        BigDecimal compareAmt = new BigDecimal(transferAmount.trim());

                        if (compareAmt.compareTo(baseAmt) > 0) {
                            newgrp.getLocalRefField("VIB.INV.TRAN.AMT")
                                  .setError("VIB.INV.TRAN.AMT must not exceed VIB.INV.AMT.");
                            hasAmountError = true;
                        }
                    } catch (NumberFormatException e) {
                        newgrp.getLocalRefField("VIB.INV.TRAN.AMT")
                              .setError("Invalid numeric value in VIB.INV.TRAN.AMT.");
                        hasAmountError = true;
                    }

                    if (!hasAmountError) {
                        newgrp.getLocalRefField("VIB.INV.TRAN.AMT").set(transferAmount);
                    }
                }

                // Replace or add group safely
                List<LocalRefGroup> groupList = ppOrderEntryRecord.getLocalRefGroups(groupName);
                if (groupList.size() > i) {
                    groupList.set(i, newgrp);
                } else {
                    groupList.add(i, newgrp);
                }
            }
        }
    }
 
    static LocalDate parseDate(String dateStr, String fieldName) {
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            // Log or handle invalid format if needed
        }
        return null;
    }

    public static String getFieldValueSafely(LocalRefGroup record, String fieldName) {
        try {
            return record.getLocalRefField(fieldName).getValue();
        } catch (Exception e) {
            logger.error("Error retrieving field {}: {}", fieldName, e.getMessage());
            return ""; // or null, depending on your use case
        }
    }

    // Validate legal document related fields based on the legalDocInfo rule
    // (Mandatory 'M' or Not allowed 'N')
    private void validateLegalDocsInfo(List<LocalRefGroup> legalDocsInfoClasses, String legalDocInfo,
            PpOrderEntryRecord ppOrderEntryRecord) {

        LocalDate transactionDate = VibPaymentHelperClass.parseDate(
                ppOrderEntryRecord.getProcessingdate().getValue(), "Processing Date");

        int i = 0;
        String groupName = "VIB.LG.ID";
        String paymentCcy = ppOrderEntryRecord.getTransactioncurrency().getValue();

        if (legalDocsInfoClasses.isEmpty() && "M".equalsIgnoreCase(legalDocInfo)) {
            LocalRefGroup newgrp = ppOrderEntryRecord.createLocalRefGroup(groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.ID", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.NAME", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.ID.ISS", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.ID.EXP", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.ID.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LG.ID.CCY", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LGL.TRAN.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LGL.LI.AMT", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LGL.LI.CCY", null, i, newgrp, groupName);
            checkMandatoryField(ppOrderEntryRecord, "VIB.LGL.LI.CHECK", null, i, newgrp, groupName);
            ppOrderEntryRecord.getLocalRefGroups(groupName).add(i, newgrp);
        } else {
            for (i = 0; i < legalDocsInfoClasses.size(); i++) {
                LocalRefGroup legalDocsInfoClass = legalDocsInfoClasses.get(i);
                LocalRefGroup newgrp = ppOrderEntryRecord.createLocalRefGroup(groupName);

                // Extract field values
                String legalDocId = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.ID");
                String legalDocName = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.NAME");
                String legalDocIssueDate = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.ID.ISS");
                String legalDocExpiryDate = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.ID.EXP");
                String legalDocAmount = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.ID.AMT");
                String legalDocCurrency = getFieldValueSafely(legalDocsInfoClass, "VIB.LG.ID.CCY");
                String legalTransferAmount = getFieldValueSafely(legalDocsInfoClass, "VIB.LGL.TRAN.AMT");
                String legalLiRemainingAmount = getFieldValueSafely(legalDocsInfoClass, "VIB.LGL.LI.AMT");
                String legalLiRemainingCurrency = getFieldValueSafely(legalDocsInfoClass, "VIB.LGL.LI.CCY");
                String legalLiCheckingFlag = getFieldValueSafely(legalDocsInfoClass, "VIB.LGL.LI.CHECK");

                // Define fields and values
                String[] fields = {
                    "VIB.LG.ID", "VIB.LG.NAME", "VIB.LG.ID.ISS", "VIB.LG.ID.EXP", "VIB.LG.ID.AMT",
                    "VIB.LG.ID.CCY", "VIB.LGL.TRAN.AMT", "VIB.LGL.LI.AMT", "VIB.LGL.LI.CCY", "VIB.LGL.LI.CHECK"
                };
                String[] values = {
                    legalDocId, legalDocName, legalDocIssueDate, legalDocExpiryDate, legalDocAmount,
                    legalDocCurrency, legalTransferAmount, legalLiRemainingAmount, legalLiRemainingCurrency, legalLiCheckingFlag
                };

                // Field-by-field validation
                for (int j = 0; j < fields.length; j++) {
                    String fieldName = fields[j];
                    String fieldValue = values[j];
                    boolean hasError = false;

                    if ("M".equalsIgnoreCase(legalDocInfo)) {
                        hasError = isNullOrEmpty(fieldValue);
                        if (hasError) {
                            setMandatoryFieldError(ppOrderEntryRecord, fieldName, i, newgrp, groupName);
                        }
                    } else if ("N".equalsIgnoreCase(legalDocInfo)) {
                        hasError = !isNullOrEmpty(fieldValue);
                        if (hasError) {
                            setInputNotAllowedError(ppOrderEntryRecord, fieldName, i, newgrp, groupName);
                        }
                    }

                    if (!hasError) {
                        newgrp.getLocalRefField(fieldName).set(fieldValue);
                    }
                }

                // --- Issue Date validation
                boolean hasIssError = false;
                if (!isNullOrEmpty(legalDocIssueDate)) {
                    LocalDate issueDate = parseDate(legalDocIssueDate, "VIB.LG.ID.ISS");
                    if (issueDate != null && issueDate.isAfter(transactionDate)) {
                        hasIssError = true;
                        List<String> errMsg = new ArrayList<>();
                        errMsg.add("EB-DATE.AFTER.PROCESSING");
                        errMsg.add("VIB.LG.ID.ISS");
                        newgrp.getLocalRefField("VIB.LG.ID.ISS").setError(errMsg.toString());
                    }
                    if (!hasIssError) {
                        newgrp.getLocalRefField("VIB.LG.ID.ISS").set(legalDocIssueDate);
                    }
                }

                // --- Expiry Date validation
                boolean hasExpError = false;
                if (!isNullOrEmpty(legalDocExpiryDate)) {
                    LocalDate expiryDate = parseDate(legalDocExpiryDate, "VIB.LG.ID.EXP");
                    if (expiryDate != null && expiryDate.isBefore(transactionDate)) {
                        hasExpError = true;
                        List<String> errMsg = new ArrayList<>();
                        errMsg.add("VIB.LG.ID.EXP is before transaction date. Please confirm to proceed.");
                        newgrp.getLocalRefField("VIB.LG.ID.EXP").setOverride(errMsg.get(0));
                    }
                    if (!hasExpError) {
                        newgrp.getLocalRefField("VIB.LG.ID.EXP").set(legalDocExpiryDate);
                    }
                }

                // --- Currency validation: If not VND, must match payment currency
                boolean hasCcyError = false;
                if (!isNullOrEmpty(legalDocCurrency)) {
                    if (!"VND".equalsIgnoreCase(legalDocCurrency) && !legalDocCurrency.equalsIgnoreCase(paymentCcy)) {
                        hasCcyError = true;
                        List<String> errMsg = new ArrayList<>();
                        errMsg.add("When Legal CCY is not VND, it must match the Payment CCY.");
                        errMsg.add("VIB.LG.ID.CCY");
                        newgrp.getLocalRefField("VIB.LG.ID.CCY").setError(errMsg.toString());
                    }
                    if (!hasCcyError) {
                        newgrp.getLocalRefField("VIB.LG.ID.CCY").set(legalDocCurrency);
                    }
                }

                // Add or replace group
                List<LocalRefGroup> groupList = ppOrderEntryRecord.getLocalRefGroups(groupName);
                if (groupList.size() > i) {
                    groupList.set(i, newgrp);
                } else {
                    groupList.add(i, newgrp);
                }
            }
        }
    }

    private void validatePassportInfo(PpOrderEntryRecord ppOrderEntryRecord, String passportInfo) {
        // Get Passport Number
        String ppNumber = ppOrderEntryRecord.getLocalRefField("VIB.PP.NUMBER").getValue();
        // Get Passport Start Date
        String ppStartDate = ppOrderEntryRecord.getLocalRefField("VIB.PP.STA.DAT").getValue();
        // Get Passport End Date
        String ppEndDate = ppOrderEntryRecord.getLocalRefField("VIB.PP.END.DAT").getValue();
        // Get Transfer Amount
        String transferAmount = ppOrderEntryRecord.getLocalRefField("VIB.PPT.TRAN.AMT").getValue();
        // Get Liability Remaining Amount
        String liRemainingAmount = ppOrderEntryRecord.getLocalRefField("VIB.PPT.LI.AMT").getValue();
        // Get Liability Remaining Currency
        String liRemainingCurrency = ppOrderEntryRecord.getLocalRefField("VIB.PPT.LI.CCY").getValue();
        // Get Liability Checking Flag
        String liCheckingFlag = ppOrderEntryRecord.getLocalRefField("VIB.PPT.LI.CHECK").getValue();
        if ("M".equalsIgnoreCase(passportInfo)) {
            // Validate mandatory fields
            checkMandatoryField(ppOrderEntryRecord, "VIB.PP.NUMBER", ppNumber);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PP.STA.DAT", ppStartDate);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PP.END.DAT", ppEndDate);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PPT.TRAN.AMT", transferAmount);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PPT.LI.AMT", liRemainingAmount);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PPT.LI.CCY", liRemainingCurrency);
            checkMandatoryField(ppOrderEntryRecord, "VIB.PPT.LI.CHECK", liCheckingFlag);
        } else if ("N".equalsIgnoreCase(passportInfo)) {
            // Validate not allowed fields
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PP.NUMBER", ppNumber);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PP.STA.DAT", ppStartDate);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PP.END.DAT", ppEndDate);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PPT.TRAN.AMT", transferAmount);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PPT.LI.AMT", liRemainingAmount);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PPT.LI.CCY", liRemainingCurrency);
            checkNotAllowedField(ppOrderEntryRecord, "VIB.PPT.LI.CHECK", liCheckingFlag);
        }
    }

    // Utility to check if string is null or empty
    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    // Check if a field is mandatory and set error if missing (Record level, with
    // index)
    private void checkMandatoryField(PpOrderEntryRecord record, String fieldName, String value, int i,
            LocalRefGroup newgrp, String groupName) {
        if (isNullOrEmpty(value)) {
            setMandatoryFieldError(record, fieldName, i, newgrp, groupName);
        }
    }

    // Set mandatory field error using indexed local field
    private void setMandatoryFieldError(PpOrderEntryRecord record, String fieldName, int i, LocalRefGroup newgrp,
            String groupName) {
        List<String> errMsg3 = new ArrayList<>();
        errMsg3.add("EB-MANDATORY.FIELD");
        errMsg3.add(fieldName);
        newgrp.getLocalRefField(fieldName).setError(errMsg3.toString());

    }

    // Set not allowed input error using indexed local field
    private void setInputNotAllowedError(PpOrderEntryRecord record, String fieldName, int i, LocalRefGroup newgrp,
            String groupName) {
        List<String> errMsg3 = new ArrayList<>();
        errMsg3.add("EB-NOT.MANDATORY.FIELD");
        errMsg3.add(fieldName);
        newgrp.getLocalRefField(fieldName).setError(errMsg3.toString());

    }

    // Overloaded method: check mandatory field by field name only (without index)
    private void checkMandatoryField(PpOrderEntryRecord record, String fieldName, String value) {
        if (isNullOrEmpty(value)) {
            setMandatoryFieldError(record, fieldName);
        }
    }

    // Overloaded method: check not allowed field by field name only (without index)
    private void checkNotAllowedField(PpOrderEntryRecord record, String fieldName, String value) {
        if (!isNullOrEmpty(value)) {
            setInputNotAllowedError(record, fieldName);
        }
    }

    // Set mandatory field error using field name reference
    private void setMandatoryFieldError(PpOrderEntryRecord record, String fieldName) {
        List<String> errMsg3 = new ArrayList<>();
        errMsg3.add("EB-MANDATORY.FIELD");
        errMsg3.add(fieldName);
        record.getLocalRefField(fieldName).setError(errMsg3.toString());
    }

    // Set not allowed input error using field name reference
    private void setInputNotAllowedError(PpOrderEntryRecord record, String fieldName) {
        List<String> errMsg3 = new ArrayList<>();
        errMsg3.add("EB-NOT.MANDATORY.FIELD");
        errMsg3.add(fieldName);
        record.getLocalRefField(fieldName).setError(errMsg3.toString());
    }

    private void setHighLevelBranch(PpOrderEntryRecord ppOrderEntryRecord) {
        try {
            DataAccess access = new DataAccess(this);

            // Step 1: Get processing company from PP Order Entry
            String processingCompany = ppOrderEntryRecord.getProcesscompany().getValue();

            // Step 2: Retrieve the company from MNEMONIC.COMPANY
            MnemonicCompanyRecord mnemonicCompanyRecord = new MnemonicCompanyRecord(
                    access.getRecord("MNEMONIC.COMPANY", processingCompany));
            String company = mnemonicCompanyRecord.getCompany().getValue();

            // Step 3: Read the COMPANY record to determine high-level branch
            CompanyRecord companyRec = new CompanyRecord(access.getRecord("COMPANY", company));
            String isHighLevel = companyRec.getLocalRefField("VIB.HIGH.LEVEL.BR").getValue();

            // Step 4: Set VIB.HIGH.LEVEL.BR based on comparison
            if (company.equalsIgnoreCase(isHighLevel)) {
                ppOrderEntryRecord.getLocalRefField("VIB.HIGH.LEVEL.BR").setValue(processingCompany);
            } else {
                ppOrderEntryRecord.getLocalRefField("VIB.HIGH.LEVEL.BR").setValue(isHighLevel);
            }
        } catch (Exception e) {
            logger.error("Error setting high level branch for record: {}", e.getMessage());
        }
    }

}
