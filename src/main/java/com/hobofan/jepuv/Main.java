package com.hobofan.jepuv;

public class Main {
    public static void main(String[] args) {
        try {
            JepSetup.setupJepLibraryNew();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
