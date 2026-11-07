package com.costeo3d.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.costeo3d.domain.model.Customer;
import com.costeo3d.infrastructure.persistence.PersistenceException;
import com.costeo3d.infrastructure.persistence.dao.CustomerDao;

public final class JdbcCustomerDao implements CustomerDao {

    private static final Logger LOGGER =
            Logger.getLogger(JdbcCustomerDao.class.getName());

    private static final String INSERT_SQL = """
            INSERT INTO cliente (nombre, telefono, email, activo)
            VALUES (?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id_cliente, nombre, telefono, email, activo
            FROM cliente
            """;

    private static final String UPDATE_SQL = """
            UPDATE cliente
            SET nombre = ?, telefono = ?, email = ?, activo = ?
            WHERE id_cliente = ?
            """;

    private final JdbcConnectionFactory connectionFactory;

    public JdbcCustomerDao(JdbcConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(
                connectionFactory, "JDBC connection factory is required");
    }

    @Override
    public Customer insert(Customer customer) {
        Objects.requireNonNull(customer, "Customer is required");
        if (customer.getId() != null) {
            throw new IllegalArgumentException("New customer must not have an ID.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            setCustomerFields(statement, customer);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Customer insert did not return a generated ID.");
                }
                return new Customer(keys.getInt(1), customer.getName(),
                        customer.getPhone(), customer.getEmail(), customer.isActive());
            }
        } catch (SQLException e) {
            throw persistenceFailure("insert", e);
        }
    }

    @Override
    public Optional<Customer> findById(int id) {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "WHERE id_cliente = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapCustomer(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw persistenceFailure("findById", e);
        }
    }

    @Override
    public List<Customer> findAll() {
        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        SELECT_SQL + "ORDER BY id_cliente");
                ResultSet results = statement.executeQuery()) {
            List<Customer> customers = new ArrayList<>();
            while (results.next()) {
                customers.add(mapCustomer(results));
            }
            return customers;
        } catch (SQLException e) {
            throw persistenceFailure("findAll", e);
        }
    }

    @Override
    public boolean update(Customer customer) {
        Objects.requireNonNull(customer, "Customer is required");
        if (customer.getId() == null) {
            throw new IllegalArgumentException("Customer must have an ID to be updated.");
        }

        try (Connection connection = connectionFactory.openConnection();
                PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
            setCustomerFields(statement, customer);
            statement.setInt(5, customer.getId());
            if (statement.executeUpdate() > 0) {
                return true;
            }
            // MySQL can report zero changed rows when values are unchanged.
            try (PreparedStatement check = connection.prepareStatement(
                    "SELECT 1 FROM cliente WHERE id_cliente = ?")) {
                check.setInt(1, customer.getId());
                try (ResultSet results = check.executeQuery()) {
                    return results.next();
                }
            }
        } catch (SQLException e) {
            throw persistenceFailure("update", e);
        }
    }

    private static void setCustomerFields(PreparedStatement statement, Customer customer)
            throws SQLException {
        statement.setString(1, customer.getName());
        statement.setString(2, customer.getPhone());
        statement.setString(3, customer.getEmail());
        statement.setBoolean(4, customer.isActive());
    }

    private static Customer mapCustomer(ResultSet results) throws SQLException {
        return new Customer(
                results.getInt("id_cliente"),
                results.getString("nombre"),
                results.getString("telefono"),
                results.getString("email"),
                results.getBoolean("activo"));
    }

    private static PersistenceException persistenceFailure(String operation, SQLException e) {
        LOGGER.log(Level.SEVERE,
                "Customer persistence operation {0} failed (SQLState: {1}, vendor code: {2}).",
                new Object[] { operation, e.getSQLState(), e.getErrorCode() });
        return new PersistenceException("Cannot " + operation + " customer data.", e);
    }
}
