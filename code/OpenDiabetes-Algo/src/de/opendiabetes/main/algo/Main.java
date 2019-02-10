package de.opendiabetes.main.algo;

import com.github.sh0nk.matplotlib4j.Plot;
import com.github.sh0nk.matplotlib4j.PythonExecutionException;
import de.opendiabetes.main.math.BasalCalc;
import de.opendiabetes.main.math.Predictions;
import de.opendiabetes.parser.Profile;
import de.opendiabetes.parser.ProfileParser;
import de.opendiabetes.parser.VaultEntryParser;
import de.opendiabetes.vault.engine.container.VaultEntry;
import de.opendiabetes.vault.engine.util.SortVaultEntryByDate;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static void main(String[] args) {

        ProfileParser profileParser = new ProfileParser();

        String profilePath = "/home/anna/Daten/Uni/14. Semester/BP/Dataset_Small/00390014/direct-sharing-31/profile_2017-07-10_to_2017-11-08.json";
        Profile profile = profileParser.parseFile(profilePath);
        profile.adjustProfile();

        BasalCalc basalCalculator = new BasalCalc(profile);
        VaultEntryParser parser = new VaultEntryParser();

        String treatmentPath = "/home/anna/Daten/Uni/14. Semester/BP/Dataset_Small/00390014/direct-sharing-31/treatments_2017-07-10_to_2017-11-08.json";
        List<VaultEntry> treatments = parser.parseFile(treatmentPath);
        treatments.sort(new SortVaultEntryByDate());
        List<VaultEntry> basalTreatments = new ArrayList<>();
        List<VaultEntry> bolusTreatment = new ArrayList<>();
        List<VaultEntry> mealTreatment = new ArrayList<>();

        for (VaultEntry treatment : treatments) {
            switch (treatment.getType()) {
                case BASAL_MANUAL:
                    basalTreatments.add(treatment);
                    break;
                case BOLUS_NORMAL:
                    bolusTreatment.add(treatment);
                    break;
                case MEAL_MANUAL:
                    mealTreatment.add(treatment);
                    break;
                default:
                    System.out.println("de.opendiabetes.main.algo.Main.main() " + treatment.getType());
                    break;
            }

        }

        List<TempBasal> basals = basalCalculator.calculateBasal(basalTreatments);
        /*
        for (TempBasal b:basals){
            System.out.println(b.toString());

        }*/

        String entriesPath = "/home/anna/Daten/Uni/14. Semester/BP/Dataset_Small/00390014/direct-sharing-31/entries_2017-07-10_to_2017-11-08.json";
        List<VaultEntry> entries = parser.parseFile(entriesPath);
        entries.sort(new SortVaultEntryByDate());

        int absorptionTime = 180;
        int insDuration = 180;
        Algorithm algo = new OpenDiabetesAlgo(absorptionTime, insDuration, profile);
//        Algorithm algo2 = new BruteForceAlgo();
        Algorithm algo2 = new NewAlgo(absorptionTime, insDuration, profile);
        algo.setGlucoseMeasurements(entries);
        algo.setBolusTreatments(bolusTreatment);
        algo.setBasalTreatments(basals);
        algo2.setGlucoseMeasurements(entries);
        algo2.setBolusTreatments(bolusTreatment);
        algo2.setBasalTreatments(basals);

        System.out.println("calc :");
        List<VaultEntry> meals = algo.calculateMeals();
        List<VaultEntry> meals2 = algo2.calculateMeals();

//        int insSensitivity = 35;
//        int carbRate = 10;
        List<Double> bgTimes = new ArrayList();
        List<Double> bgValues = new ArrayList();
        List<Double> predValues = new ArrayList();
        List<Double> algoValues = new ArrayList();
        List<Double> algo2Values = new ArrayList();
        double start = entries.get(0).getValue();
        for (VaultEntry ve : entries) {
             
            double algoPredict = Predictions.predict(ve.getTimestamp().getTime(),
                    meals, bolusTreatment, basals,
                    profile.getSensitivity(), insDuration,
                    profile.getCarbratio(), absorptionTime);
            double algo2Predict = Predictions.predict(ve.getTimestamp().getTime(),
                    meals2, bolusTreatment, basals,
                    profile.getSensitivity(), insDuration,
                    profile.getCarbratio(), absorptionTime);
            double dataPredict = Predictions.predict(ve.getTimestamp().getTime(),
                    mealTreatment, bolusTreatment, basals,
                    profile.getSensitivity(), insDuration,
                    profile.getCarbratio(), absorptionTime);
//            if (predValues.isEmpty()) {
                predValues.add(start + dataPredict);
                algoValues.add(start + algoPredict);
                algo2Values.add(start + algo2Predict);
//                predValues.add(ve.getValue() + dataPredict);
//                algoValues.add(ve.getValue() + algoPredict);
//                algo2Values.add(ve.getValue() + algo2Predict);
//            } else {
//                predValues.add(predValues.get(predValues.size() - 1) + dataPredict);
//                algoValues.add(algoValues.get(algoValues.size() - 1) + algoPredict);
//                algo2Values.add(algo2Values.get(algo2Values.size() - 1) + algo2Predict);
//            }
            bgTimes.add(ve.getTimestamp().getTime() / 1000.0);
            bgValues.add(ve.getValue());

            if (bgTimes.get(bgTimes.size() - 1) - bgTimes.get(0) > 5 * 24 * 60 * 60) {
                break;
            }
        }

//        List<Double> mealTimes = new ArrayList();
//        List<Double> mealValues = new ArrayList();
//        for (VaultEntry ve: mealTreatment) {
//            mealTimes.add(ve.getTimestamp().getTime()/1000.0);
//            mealValues.add(ve.getValue());
//        }
//        List<Double> estMealTimes = new ArrayList();
//        List<Double> estMealValues = new ArrayList();
//        for (VaultEntry ve: meals) {
//            estMealTimes.add(ve.getTimestamp().getTime()/1000.0);
//            estMealValues.add(ve.getValue());
//        } 
        Plot plt = Plot.create();
        plt.plot().addDates(bgTimes).add(bgValues);
        plt.plot().addDates(bgTimes).add(predValues).color("red");
        plt.plot().addDates(bgTimes).add(algoValues).color("green");
        plt.plot().addDates(bgTimes).add(algo2Values).color("cyan");
//        plt.plot().addDates(mealTimes).add(mealValues).ls("").marker("x");
//        plt.plot().addDates(estMealTimes).add(estMealValues).ls("").marker("o");
        try {
            plt.show();
        } catch (IOException | PythonExecutionException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

//        for (VaultEntry meal : meals) {
//            System.out.println(meal.toString());
//        }
//        //FOR LATER USE
//        // query data
//        List<VaultEntry> data = VaultDao.getInstance().queryAllVaultEntries();
//
//        if (data == null || data.isEmpty()) {
//            Logger.getLogger(Main.class.getName()).severe("Database empty after processing");
//            System.exit(0);
//        } else {
//            // export Data
//            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMM-HHmmss");
//            String odvExpotFileName = "export-"
//                    + VaultCsvEntry.VERSION_STRING
//                    + "-"
//                    + formatter.format(new Date())
//                    + ".csv";
//
//            String path = "./"; //System.getProperty("java.io.tmpdir");
//            odvExpotFileName = new File(path).getAbsolutePath()
//                    + "/" + odvExpotFileName;
//
//            ExporterOptions eOptions = new ExporterOptions(
//                    true, //export all
//                    null, //from date
//                    null // to date     
//            );
//
//            // standard export
//            FileExporter exporter = new VaultCsvExporter(eOptions,
//                    VaultDao.getInstance(),
//                    odvExpotFileName);
//            int result = exporter.exportDataToFile(null);
//            if (result != VaultCsvExporter.RESULT_OK) {
//                Logger.getLogger(Main.class.getName()).severe("Export Error");
//                System.exit(0);
//            }
//        }
    }
}
