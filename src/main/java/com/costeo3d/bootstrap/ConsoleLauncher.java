package com.costeo3d.bootstrap;

import com.costeo3d.presentation.console.ConsoleApplication;

public class ConsoleLauncher {

    public static void main(String[] args) {
        DemoDataLoader loader = new DemoDataLoader();
        DemoData data = loader.load();

        ConsoleApplication application =
                new ConsoleApplication(data);

        application.run();
    }
}