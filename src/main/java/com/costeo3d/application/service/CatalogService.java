package com.costeo3d.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.domain.model.Material;
import com.costeo3d.domain.model.Model3D;
import com.costeo3d.domain.model.Spool;
import com.costeo3d.infrastructure.persistence.dao.CustomerDao;
import com.costeo3d.infrastructure.persistence.dao.MaterialDao;
import com.costeo3d.infrastructure.persistence.dao.Model3DDao;
import com.costeo3d.infrastructure.persistence.dao.SpoolDao;

/**
 * Application entry point for the four persistent catalogues.
 *
 * Business validation stays in the domain model; SQL and connection management
 * stay in the DAO implementations.
 */
public class CatalogService {

    private final MaterialDao materialDao;
    private final SpoolDao spoolDao;
    private final Model3DDao model3DDao;
    private final CustomerDao customerDao;

    public CatalogService(
            MaterialDao materialDao,
            SpoolDao spoolDao,
            Model3DDao model3DDao,
            CustomerDao customerDao) {
        this.materialDao = Objects.requireNonNull(materialDao, "MaterialDao is required.");
        this.spoolDao = Objects.requireNonNull(spoolDao, "SpoolDao is required.");
        this.model3DDao = Objects.requireNonNull(model3DDao, "Model3DDao is required.");
        this.customerDao = Objects.requireNonNull(customerDao, "CustomerDao is required.");
    }

    // Materials
    public Material createMaterial(Material material) {
        return materialDao.insert(material);
    }

    public Optional<Material> findMaterialById(int id) {
        return materialDao.findById(id);
    }

    public List<Material> listMaterials() {
        return materialDao.findAll();
    }

    public boolean updateMaterial(Material material) {
        return materialDao.update(material);
    }

    // Spools
    public Spool createSpool(Spool spool) {
        return spoolDao.insert(spool);
    }

    public Optional<Spool> findSpoolById(int id) {
        return spoolDao.findById(id);
    }

    public List<Spool> listSpools() {
        return spoolDao.findAll();
    }

    public boolean updateSpool(Spool spool) {
        return spoolDao.update(spool);
    }

    // 3D models
    public Model3D createModel3D(Model3D model) {
        return model3DDao.insert(model);
    }

    public Optional<Model3D> findModel3DById(int id) {
        return model3DDao.findById(id);
    }

    public List<Model3D> listModels3D() {
        return model3DDao.findAll();
    }

    public boolean updateModel3D(Model3D model) {
        return model3DDao.update(model);
    }

    // Customers
    public Customer createCustomer(Customer customer) {
        return customerDao.insert(customer);
    }

    public Optional<Customer> findCustomerById(int id) {
        return customerDao.findById(id);
    }

    public List<Customer> listCustomers() {
        return customerDao.findAll();
    }

    public boolean updateCustomer(Customer customer) {
        return customerDao.update(customer);
    }
}
