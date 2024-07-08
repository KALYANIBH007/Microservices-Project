package com.boot.order_service.service;

import com.boot.order_service.dto.InventoryResponse;
import com.boot.order_service.dto.OrderItemsRequest;
import com.boot.order_service.dto.OrderRequest;
import com.boot.order_service.model.Order;
import com.boot.order_service.model.OrderItems;
import com.boot.order_service.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    public void createOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderItems> orderItems1 = orderRequest.getOrderItemsRequest()
                .stream()
                .map(this::OrderItemReqToOrderItem)
                .toList();

        order.setOrderItems(orderItems1);

        // For all OrderItems we are retrieving there skuCode List

        List<String> skuCodeList = order.getOrderItems().stream()
                        .map(OrderItems::getSkuCode)
                        .toList();

        //Checking if the product is present in the stock or not
        //Order service & Inventory service communication established using webclient
        InventoryResponse[] inventoryResponseArray =
                webClientBuilder.build().get()
                        .uri("http://inventory-service/api/inventory"
                                , uriBuilder -> uriBuilder.queryParam("skuCode", skuCodeList).build())
                        .retrieve()
                        .bodyToMono(InventoryResponse[].class)
                        .block();

        boolean productsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse :: isInStock);

        if(productsInStock)
            orderRepository.save(order);
        else
            throw new IllegalArgumentException("Product is not present in the stock, Please try again later");
    }

    private OrderItems OrderItemReqToOrderItem(OrderItemsRequest orderItemsRequest){

        OrderItems orderItems = new OrderItems();
        orderItems.setPrice(orderItemsRequest.getPrice());
        orderItems.setQuantity(orderItemsRequest.getQuantity());
        orderItems.setSkuCode(orderItemsRequest.getSkuCode());

        return orderItems;

    }
}
