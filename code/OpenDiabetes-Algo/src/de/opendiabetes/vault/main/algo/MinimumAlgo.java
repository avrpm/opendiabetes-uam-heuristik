package de.opendiabetes.vault.main.algo;

import de.opendiabetes.vault.main.dataprovider.AlgorithmDataProvider;
import de.opendiabetes.vault.main.math.Filter;
import de.opendiabetes.vault.main.math.Predictions;
import de.opendiabetes.vault.parser.Profile;
import de.opendiabetes.vault.container.VaultEntry;
import de.opendiabetes.vault.container.VaultEntryType;
import de.opendiabetes.vault.util.TimestampUtils;

import java.util.ArrayList;
import java.util.List;

public class MinimumAlgo extends Algorithm {


    public MinimumAlgo(long absorptionTime, long insulinDuration, Profile profile) {
        super(absorptionTime, insulinDuration, profile);
    }

    public MinimumAlgo(long absorptionTime, long insulinDuration, AlgorithmDataProvider dataProvider) {
        super(absorptionTime, insulinDuration, dataProvider);
    }

    @Override
    public List<VaultEntry> calculateMeals() {
        List<VaultEntry> mealTreatments = new ArrayList<>();


        for (int i = 0; i < glucose.size(); i++) {
            VaultEntry current = glucose.get(i);

            double mealValue = 0;

            for (int j = i + 1; j < glucose.size(); j++) {
                VaultEntry next = glucose.get(j);
                long dTime = Math.round((next.getTimestamp().getTime() - current.getTimestamp().getTime()) / 60000.0);
                if (dTime > absorptionTime / 4) {
                    break;
                }
                double currentPrediction = Predictions.predict(current.getTimestamp().getTime(), mealTreatments, bolusTreatments, basalTreatments, profile.getSensitivity(), insulinDuration, profile.getCarbratio(), absorptionTime);
                double nextPrediction = Predictions.predict(next.getTimestamp().getTime(), mealTreatments, bolusTreatments, basalTreatments, profile.getSensitivity(), insulinDuration, profile.getCarbratio(), absorptionTime);
                double deltaBg = Filter.getMedian(glucose, j, 3, absorptionTime / 4) - Filter.getMedian(glucose, i, 3, absorptionTime / 4);
                //double deltaBg = Filter.getAverage(glucose, j, 5, absorptionTime / 3) - Filter.getAverage(glucose, i, 5, absorptionTime / 3);
                //double deltaBg = next.getValue() - current.getValue();
                double deltaPrediction = (nextPrediction - currentPrediction);
                double value = calcMealValue(deltaBg - deltaPrediction, dTime);
                if (j == i + 1 || value < mealValue) {
                    mealValue = value;
                }
            }

            if (mealValue > 0) {
                VaultEntry meal = new VaultEntry(VaultEntryType.MEAL_MANUAL, TimestampUtils.createCleanTimestamp(current.getTimestamp()), mealValue);
                mealTreatments.add(meal);
            }
        }

        return mealTreatments;
    }

    private double calcMealValue(double deltaBg, double deltaTime) {
        return deltaBg * profile.getCarbratio() / (profile.getSensitivity() * Predictions.carbsOnBoard(deltaTime, absorptionTime));
    }
}