package org.mdpnp.apps.testapp.acliva;

import java.io.IOException;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import org.mdpnp.apps.testapp.IceApplicationProvider;
import org.springframework.context.ApplicationContext;

public class AclivaTestApplicationFactory implements IceApplicationProvider {

    private final IceApplicationProvider.AppType AclivaTestApplication = new IceApplicationProvider.AppType(
            "ACLIVA Test", "NO_ACLIVA", (URL) null, 0.75, false
    );

    @Override
    public IceApplicationProvider.AppType getAppType() {
        return AclivaTestApplication;
    }

    @Override
    public IceApplicationProvider.IceApp create(ApplicationContext context) throws IOException {
        return new AclivaTestApp();
    }

    private class AclivaTestApp implements IceApplicationProvider.IceApp {

        private final Parent ui;
        private final AclivaTestApplication controller;

        public AclivaTestApp() throws IOException {
            FXMLLoader loader = new FXMLLoader(AclivaTestApplication.class.getResource("AclivaTestApplication.fxml"));
            ui = loader.load();
            controller = loader.getController();
        }

        @Override
        public AppType getDescriptor() {
            return AclivaTestApplication;
        }

        @Override
        public Parent getUI() {
            return ui;
        }

        @Override
        public void activate(ApplicationContext context) {
        }

        @Override
        public void stop() throws Exception {
        }

        @Override
        public void destroy() throws Exception {
        }
        
        @Override
        public int getPreferredWidth() {
            return 1440;
        }
        
        @Override
        public int getPreferredHeight() {
            return 1080;
        }
    }
}
