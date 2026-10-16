package com.erikjarquin.compras.mapper;

import org.springframework.stereotype.Component;

import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;

/**
 * Mapper caja -> CashResponse (componente de Spring, inyectado por
 * CashRegisterImpl). closingAmount es el dinero CONTADO por el cajero
 * (countedAmount), distinto del esperado según sistema.
 */
@Component
public class CashRegisterMapper {
    public CashResponse toResponse(CashRegisterEntity cash){
        if(cash == null){
            return null;
        }

        CashResponse response = new CashResponse();

        response.setId(cash.getId());
        response.setNumber(cash.getNumber());
        response.setDifferenceReason(cash.getDifferenceReason());
        response.setOpenedAt(cash.getOpenedAt());
        response.setClosedAt(cash.getClosedAt());
        response.setOpeningAmount(cash.getOpeningAmount());
        response.setClosingAmount(cash.getCountedAmount()); //Dinero contado por el cajero
        response.setActive(cash.getActive());
        response.setExpectedAmount(cash.getExpectedAmount());
        response.setDifference(cash.getDifference());
        response.setCashSales(cash.getCashSales());
        response.setDebitSales(cash.getDebitSales());
        response.setCreditSales(cash.getCreditSales());
        response.setTotalSales(cash.getTotalSales());
        response.setTotalTickets(cash.getTotalTickets());

        return response;
    }
}
