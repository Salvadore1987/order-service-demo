# Order Service

## Overview
Сервис предназначен для обработки процесса заказа товаров.
Данный сервис предназначен для только для тестирования движка https://github.com/Salvadore1987/process-manager-engine и не несет никакой логической нагрузки

## Process Overview
1. Необходимо реализовать REST endpoint POST /orders, который принимает следующую модель
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "itemId": 1,
  "amount": "100.00",
  "quantity": 2,
  "deliveryAddress": {
    "city": "Tashkent",
    "country": "UZB",
    "street": "A. Navoi",
    "district": "Mirzo-Ulugbek",
    "house": "8",
    "flat": 54
  }
}
```
2. Далее заказ сохраняется в БД (H2) со статусом CREATED
3. И создается процесс в системе Process Engine по адресу http://localhost:8080/api/v1/instances с именем order-process
4. Так же необходимо под каждый таск процесса реализовать Worker без реализации а только с логированием

## Prepare
** При поднятии сервиса необходимо сначала задеплоить процесс расположенный внутри ресурсов проекта с именем: order-process.bpmn
** Документация по интеграции с движком расположена по адресу: https://github.com/Salvadore1987/process-manager-engine/blob/main/docs/integration-guide.md 
