package de.opendiabetes.algo;

import de.opendiabetes.parser.Profile;
import de.opendiabetes.parser.ProfileParser;
import de.opendiabetes.parser.VaultEntryParser;
import de.opendiabetes.vault.engine.container.VaultEntry;
import de.opendiabetes.vault.engine.container.VaultEntryType;
import de.opendiabetes.vault.engine.util.SortVaultEntryByDate;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        ProfileParser profileParser = new ProfileParser();
        String profilePath = "./profile_2017-07-10_to_2017-11-08.json";
        Profile profile = profileParser.parseFile(profilePath);
        profile.adjustProfile();

        BasalCalc basalCalculator = new BasalCalc(profile);
        VaultEntryParser parser = new VaultEntryParser();

        String treatmentPath = "./treatments_2017-07-10_to_2017-11-08.json";
        List<VaultEntry> treatments = parser.parseFile(treatmentPath);
        treatments.sort(new SortVaultEntryByDate());
        List<VaultEntry> basalTreatments = new ArrayList<>();
        List<VaultEntry> bolusTreatment = new ArrayList<>();
        List<VaultEntry> mealTreatment = new ArrayList<>();

        for (VaultEntry treatment : treatments) {
            if (treatment.getType().equals(VaultEntryType.BASAL_MANUAL)) {
                basalTreatments.add(treatment);
            } else if (treatment.getType().equals(VaultEntryType.BOLUS_NORMAL)) {
                bolusTreatment.add(treatment);
            } else if (treatment.getType().equals(VaultEntryType.MEAL_MANUAL)) {
                mealTreatment.add(treatment);
            }

        }

        List<TempBasal> basals = basalCalculator.calculateBasal(basalTreatments);
        /*
        for (TempBasal b:basals){
            System.out.println(b.toString());

        }*/

        String entriesPath = "./entries_2017-07-10_to_2017-11-08.json";
        List<VaultEntry> entries = parser.parseFile(entriesPath);
        entries.sort(new SortVaultEntryByDate());

        OpenDiabetesAlgo algo = new OpenDiabetesAlgo();
        algo.setGlucoseMeasurements(entries);
        algo.setBolusTreatments(bolusTreatment);
        algo.setBasalTratments(basals);

        System.out.println("calc :");
        List<VaultEntry> meals = algo.calculateMeals();
        for (VaultEntry meal : meals) {
            System.out.println(meal.toString());
        }

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