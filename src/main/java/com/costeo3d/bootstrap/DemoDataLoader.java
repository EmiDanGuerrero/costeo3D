package com.costeo3d.bootstrap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Spool;

public class DemoDataLoader {

	public DemoData load() {
	    List<Material> materials = createMaterials();
	    List<Spool> spools = createSpools(materials);
	    List<Model3D> models = createModels();
	    List<Customer> customers = createCustomers();

	    return new DemoData(
	            materials,
	            spools,
	            models,
	            customers,
	            new ArrayList<>(),
	            new ArrayList<>(),
	            new ArrayList<>(),
	            new ArrayList<>());
	}

    private List<Material> createMaterials() {
        List<Material> materials = new ArrayList<>();

        materials.add(new Material(
                "Elegoo",
                "PLA",
                "Matte",
                new BigDecimal("18.50")));

        materials.add(new Material(
                "eSUN",
                "PLA",
                "Silk",
                new BigDecimal("22.80")));

        materials.add(new Material(
                "Grilon3",
                "PETG",
                "Glossy",
                new BigDecimal("25.40")));

        materials.add(new Material(
                "Creality",
                "ABS",
                "Matte",
                new BigDecimal("24.90")));

        return materials;
    }

    private List<Spool> createSpools(List<Material> materials) {
        List<Spool> spools = new ArrayList<>();

        Spool blackPla = new Spool(
                materials.get(0),
                "Black",
                new BigDecimal("1000"));

        blackPla.updateCurrentWeight(
                new BigDecimal("720"));

        Spool whitePla = new Spool(
                materials.get(0),
                "White",
                new BigDecimal("1000"));

        whitePla.updateCurrentWeight(
                new BigDecimal("110"));

        Spool goldSilk = new Spool(
                materials.get(1),
                "Gold",
                new BigDecimal("1000"));

        goldSilk.updateCurrentWeight(
                new BigDecimal("640"));

        Spool bluePetg = new Spool(
                materials.get(2),
                "Blue",
                new BigDecimal("1000"));

        bluePetg.updateCurrentWeight(
                new BigDecimal("430"));

        Spool grayAbs = new Spool(
                materials.get(3),
                "Gray",
                new BigDecimal("1000"));

        grayAbs.updateCurrentWeight(
                new BigDecimal("850"));

        spools.add(blackPla);
        spools.add(whitePla);
        spools.add(goldSilk);
        spools.add(bluePetg);
        spools.add(grayAbs);

        return spools;
    }

    private List<Model3D> createModels() {
        List<Model3D> models = new ArrayList<>();

        models.add(new Model3D(
                "Geometric Planter",
                null,
                "Printables",
                new BigDecimal("125"),
                300));

        models.add(new Model3D(
                "Dragon Figure",
                null,
                "Thingiverse",
                new BigDecimal("185"),
                480));

        models.add(new Model3D(
                "Headphone Stand",
                null,
                "Printables",
                new BigDecimal("240"),
                620));

        models.add(new Model3D(
                "Cable Organizer",
                null,
                "MakerWorld",
                new BigDecimal("45"),
                95));

        return models;
    }

    private List<Customer> createCustomers() {
        List<Customer> customers = new ArrayList<>();

        customers.add(new Customer(
                "Juan Perez",
                "3874000001",
                "juan@example.com"));

        customers.add(new Customer(
                "Sofia Martinez",
                "3874000002",
                "sofia@example.com"));

        customers.add(new Customer(
                "Lucas Fernandez",
                "3874000003",
                "lucas@example.com"));

        return customers;
    }
}