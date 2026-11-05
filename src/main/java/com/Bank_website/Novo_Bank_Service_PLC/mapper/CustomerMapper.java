package com.Bank_website.Novo_Bank_Service_PLC.mapper;

import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    /** Maps request fields only; system fields (number, statuses, timestamps) are set by the service. */
    public Customer toEntity(CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setDisplayName(request.firstName().trim() + " " + request.lastName().trim());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setMobileNo(request.mobileNo());
        customer.setEmail(request.email() == null || request.email().isBlank() ? null : request.email());
        customer.setIdType(request.idType());
        customer.setIdNumber(request.idNumber());
        return customer;
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getDisplayName(),
                customer.getMobileNo(),
                customer.getEmail(),
                customer.getKycStatus(),
                customer.getAmlRiskRating(),
                customer.getStatus(),
                customer.getCreatedAt());
    }
}
