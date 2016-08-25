package navigation.ai.fibem.com.classicnavigation.drone;

import android.content.Context;

import org.encog.app.analyst.EncogAnalyst;
import org.encog.app.analyst.util.AnalystUtility;
import org.encog.neural.networks.BasicNetwork;
import org.encog.persist.EncogDirectoryPersistence;

import navigation.ai.fibem.com.classicnavigation.R;

/**
 * Singleton class to hold network data. Avoids multiple time consuming loads
 */
public class PilotNetwork {
    private static PilotNetwork mInstance = null;

    private BasicNetwork mNetwork;
    private AnalystUtility mUtil;

    private PilotNetwork() {
    }

    /**
     * Returns an instance of the PilotNetwork class
     *
     * @param context Context for reading from resources directory
     * @return The created instance
     */
    public static PilotNetwork getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new PilotNetwork();

            // Load the analyst and obtain the utility
            EncogAnalyst analyst = new EncogAnalyst();
            analyst.load(context.getResources().openRawResource(R.raw.drone));
            mInstance.mUtil = analyst.getUtility();

            // Load the neural network
            mInstance.mNetwork = (BasicNetwork) EncogDirectoryPersistence.loadObject(context.getResources().
                    openRawResource(R.raw.drone_train));
        }

        return mInstance;
    }

    public BasicNetwork getNetwork() {
        return mNetwork;
    }

    public AnalystUtility getUtility() {
        return mUtil;
    }
}