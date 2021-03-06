package pl.poznan.put.promethee;

import pl.poznan.put.promethee.xmcda.InputsHandler;
import pl.poznan.put.promethee.xmcda.OutputsHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Created by Maciej Uniejewski on 2016-12-10.
 */
public class FlowSortGDSS {

    private static final String LOWER = "LOWER";
    private static final String UPPER = "UPPER";

    private FlowSortGDSS() {
        throw new IllegalAccessError("Utility class");
    }

    public static OutputsHandler.Output sort(InputsHandler.Inputs inputs) {

        countProfilesSummaryFlows(inputs);
        OutputsHandler.Output output = new OutputsHandler.Output();

        if ("bounding".equalsIgnoreCase(inputs.getProfilesType().toString())) {
            sortWithLimitingProfiles(inputs, output);
        } else {
            sortWithCentralProfiles(inputs, output);
        }

        return output;
    }

    private static void countProfilesSummaryFlows(InputsHandler.Inputs inputs) {
        inputs.setProfilesSummaryFlows(new LinkedHashMap<>());

        for (int i = 0; i < inputs.getProfilesIds().size(); i++) {
            for (int j = 0; j < inputs.getProfilesIds().get(i).size(); j++) {
                for (int k = 0; k < inputs.getAlternativesIds().size(); k++) {
                    String profileId = inputs.getProfilesIds().get(i).get(j);
                    String alternativeId = inputs.getAlternativesIds().get(k);

                    Integer profilesNumber = inputs.getProfilesIds().size() * inputs.getProfilesIds().get(i).size();
                    BigDecimal leftFlow = inputs.getProfilesFlows().get(profileId).multiply(new BigDecimal(profilesNumber));
                    BigDecimal rightFlow = inputs.getPreferences().get(i).get(profileId).get(alternativeId).subtract(inputs.getPreferences().get(i).get(alternativeId).get(profileId));

                    BigDecimal flow = (leftFlow.add(rightFlow)).divide(new BigDecimal(profilesNumber + 1), 6, RoundingMode.HALF_UP);

                    inputs.getProfilesSummaryFlows().putIfAbsent(profileId, new HashMap<>());
                    inputs.getProfilesSummaryFlows().get(profileId).put(alternativeId, flow);
                }
            }
        }
    }

    private static void sortWithLimitingProfiles(InputsHandler.Inputs inputs, OutputsHandler.Output output) {
        Map<String, String> assignments = new LinkedHashMap<>();
        Map<String, Map<String, String>> firstStepAssignments = new LinkedHashMap<>();

        for (int i = 0; i < inputs.getAlternativesIds().size(); i++) {
            Integer firstClassNumber = null;
            Integer secondClassNumber = null;
            Set<Integer> firstClassDecisionMakers = new HashSet<>();
            Set<Integer> secondClassDecisionMakers = new HashSet<>();
            String alternativeId = inputs.getAlternativesIds().get(i);
            for (int decisionMaker = 0; decisionMaker < inputs.getProfilesIds().size(); decisionMaker++) {
                Integer decisionMakerClassNumber = null;
                for (int profile = 0; profile < inputs.getProfilesIds().get(decisionMaker).size(); profile++) {
                    String profilesId = inputs.getProfilesIds().get(decisionMaker).get(profile);
                    if (inputs.getAlternativesFlowsAverage().get(alternativeId).compareTo(inputs.getProfilesSummaryFlows().get(profilesId).get(alternativeId)) <= 0 && decisionMakerClassNumber == null) {
                        decisionMakerClassNumber = profile;
                        break;
                    }
                }
                if (decisionMakerClassNumber == null && inputs.getAlternativesFlowsAverage().get(alternativeId).compareTo(inputs.getProfilesSummaryFlows().get(inputs.getProfilesIds().get(decisionMaker).get(inputs.getProfilesIds().get(decisionMaker).size() - 1)).get(alternativeId)) > 0) {
                    decisionMakerClassNumber = inputs.getProfilesIds().get(decisionMaker).size();
                }
                if (firstClassNumber == null) {
                    firstClassNumber = decisionMakerClassNumber;
                    firstClassDecisionMakers.add(decisionMaker);
                } else if (firstClassNumber.intValue() != decisionMakerClassNumber) {
                    secondClassNumber = decisionMakerClassNumber;
                    secondClassDecisionMakers.add(decisionMaker);
                } else {
                    firstClassDecisionMakers.add(decisionMaker);
                }
            }

            if (secondClassNumber == null) {
                String classId = inputs.getCategoryProfiles().get(0).get(firstClassNumber).getCategory().id();
                assignments.put(alternativeId, classId);
                Map<String, String> interval = new LinkedHashMap<>();
                interval.put(LOWER,classId);
                interval.put(UPPER, classId);
                firstStepAssignments.put(alternativeId, interval);
            } else {
                String leftClassId = inputs.getCategoryProfiles().get(0).get(Math.min(firstClassNumber, secondClassNumber)).getCategory().id();
                String rightClassId = inputs.getCategoryProfiles().get(0).get(Math.max(firstClassNumber, secondClassNumber)).getCategory().id();
                Map<String, String> interval = new LinkedHashMap<>();
                interval.put(LOWER,leftClassId);
                interval.put(UPPER, rightClassId);
                firstStepAssignments.put(alternativeId, interval);

                BigDecimal profileK;
                BigDecimal profileK1;

                if (firstClassNumber < secondClassNumber) {
                    profileK = countDkForLimitingProfiles(alternativeId, firstClassNumber, firstClassDecisionMakers, inputs);
                    profileK1 = countDk1ForLimitingProfiles(alternativeId, secondClassNumber, secondClassDecisionMakers, inputs);
                } else {
                    profileK = countDkForLimitingProfiles(alternativeId, secondClassNumber, secondClassDecisionMakers, inputs);
                    profileK1 = countDk1ForLimitingProfiles(alternativeId, firstClassNumber, firstClassDecisionMakers, inputs);
                }

                if (profileK1.compareTo(profileK) > 0) {
                    assignments.put(alternativeId, leftClassId);
                } else if (profileK1.compareTo(profileK) < 0) {
                    assignments.put(alternativeId, rightClassId);
                } else {
                    if (inputs.getAssignToABetterClass()) {
                        assignments.put(alternativeId, rightClassId);
                    } else {
                        assignments.put(alternativeId, leftClassId);
                    }
                }
            }
        }
        output.setAssignments(assignments);
        output.setFirstStepAssignments(firstStepAssignments);
    }

    private static BigDecimal countDkForLimitingProfiles(String alternativeId, Integer profile, Set<Integer> decisionMakers, InputsHandler.Inputs inputs) {
        BigDecimal sum = BigDecimal.ZERO;

        for (Integer decisionMaker: decisionMakers) {
            String profileId = inputs.getProfilesIds().get(decisionMaker).get(profile);

            BigDecimal phiRk = inputs.getProfilesSummaryFlows().get(profileId).get(alternativeId);
            BigDecimal phiAi = inputs.getAlternativesFlowsAverage().get(alternativeId);
            BigDecimal subtractionResult = phiAi.subtract(phiRk);
            BigDecimal weight = inputs.getDecisionMakersWages().get(decisionMaker);

            sum = sum.add(weight.multiply(subtractionResult));
        }

        return sum;
    }

    private static BigDecimal countDk1ForLimitingProfiles(String alternativeId, Integer profile, Set<Integer> decisionMakers, InputsHandler.Inputs inputs) {
        BigDecimal sum = BigDecimal.ZERO;

        for (Integer decisionMaker: decisionMakers) {
            String profileId = inputs.getProfilesIds().get(decisionMaker).get(profile);

            BigDecimal phiRk1 = inputs.getProfilesSummaryFlows().get(profileId).get(alternativeId);
            BigDecimal phiAi = inputs.getAlternativesFlowsAverage().get(alternativeId);
            BigDecimal subtractionResult = phiRk1.subtract(phiAi);
            BigDecimal weight = inputs.getDecisionMakersWages().get(decisionMaker);

            sum = sum.add(weight.multiply(subtractionResult));
        }

        return sum;
    }

    private static void sortWithCentralProfiles(InputsHandler.Inputs inputs, OutputsHandler.Output output) {
        Map<String, String> assignments = new LinkedHashMap<>();
        Map<String, Map<String, String>> firstStepAssignments = new LinkedHashMap<>();

        for (int i = 0; i < inputs.getAlternativesIds().size(); i++) {
            Integer firstClassNumber = null;
            Integer secondClassNumber = null;
            Set<Integer> firstClassDecisionMakers = new HashSet<>();
            Set<Integer> secondClassDecisionMakers = new HashSet<>();
            String alternativeId = inputs.getAlternativesIds().get(i);
            for (int decisionMaker = 0; decisionMaker < inputs.getProfilesIds().size(); decisionMaker++) {
                Integer nearestClass = null;
                BigDecimal distance = BigDecimal.ZERO;
                for (int profile = 0; profile < inputs.getProfilesIds().get(decisionMaker).size(); profile++) {
                    String profilesId = inputs.getProfilesIds().get(decisionMaker).get(profile);
                    BigDecimal tmpDist = (inputs.getProfilesSummaryFlows().get(profilesId).get(alternativeId).subtract(inputs.getAlternativesFlowsAverage().get(alternativeId))).abs();

                    if (tmpDist.compareTo(distance) < 0 || nearestClass == null) {
                        nearestClass = profile;
                        distance = tmpDist;
                    }
                }

                if (firstClassNumber == null) {
                    firstClassNumber = nearestClass;
                    firstClassDecisionMakers.add(decisionMaker);
                } else if (nearestClass != null && firstClassNumber.intValue() != nearestClass.intValue()) {
                    secondClassNumber = nearestClass;
                    secondClassDecisionMakers.add(decisionMaker);
                } else {
                    firstClassDecisionMakers.add(decisionMaker);
                }
            }
            if (secondClassNumber == null) {
                String classId = inputs.getCategoryProfiles().get(0).get(firstClassNumber).getCategory().id();
                assignments.put(alternativeId, classId);
                Map<String, String> interval = new LinkedHashMap<>();
                interval.put(LOWER,classId);
                interval.put(UPPER, classId);
                firstStepAssignments.put(alternativeId, interval);
            } else {
                String leftClassId = inputs.getCategoryProfiles().get(0).get(Math.min(firstClassNumber, secondClassNumber)).getCategory().id();
                String rightClassId = inputs.getCategoryProfiles().get(0).get(Math.max(firstClassNumber, secondClassNumber)).getCategory().id();
                Map<String, String> interval = new LinkedHashMap<>();
                interval.put(LOWER,leftClassId);
                interval.put(UPPER, rightClassId);
                firstStepAssignments.put(alternativeId, interval);

                BigDecimal profileK;
                BigDecimal profileK1;

                if (firstClassNumber.intValue() < secondClassNumber.intValue()) {
                    profileK = countDkForCentralProfiles(alternativeId, firstClassNumber, firstClassDecisionMakers, inputs);
                    profileK1 = countDkForCentralProfiles(alternativeId, secondClassNumber, secondClassDecisionMakers, inputs);
                } else {
                    profileK = countDkForCentralProfiles(alternativeId, secondClassNumber, secondClassDecisionMakers, inputs);
                    profileK1 = countDkForCentralProfiles(alternativeId, firstClassNumber, firstClassDecisionMakers, inputs);
                }

                if (profileK1.compareTo(profileK) > 0) {
                    assignments.put(alternativeId, leftClassId);
                } else if (profileK1.compareTo(profileK) < 0) {
                    assignments.put(alternativeId, rightClassId);
                } else {
                    if (inputs.getAssignToABetterClass()) {
                        assignments.put(alternativeId, rightClassId);
                    } else {
                        assignments.put(alternativeId, leftClassId);
                    }
                }
            }
        }
        output.setAssignments(assignments);
        output.setFirstStepAssignments(firstStepAssignments);
    }

    private static BigDecimal countDkForCentralProfiles(String alternativeId, Integer profile, Set<Integer> decisionMakers, InputsHandler.Inputs inputs) {
        BigDecimal sum = BigDecimal.ZERO;

        for (Integer decisionMaker: decisionMakers) {
            String profileId = inputs.getProfilesIds().get(decisionMaker).get(profile);
            sum = sum.add(inputs.getDecisionMakersWages().get(decisionMaker).multiply((inputs.getProfilesSummaryFlows().get(profileId).get(alternativeId).subtract(inputs.getAlternativesFlowsAverage().get(alternativeId))).abs()));
        }

        return sum;
    }
}
