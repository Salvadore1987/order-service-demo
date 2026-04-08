package uz.salvadore.orderservice.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public class DeliveryAddress {

    private String city;
    private String country;
    private String street;
    private String district;
    private String house;
    private Integer flat;

    protected DeliveryAddress() {
    }

    public DeliveryAddress(String city, String country, String street,
                           String district, String house, Integer flat) {
        this.city = city;
        this.country = country;
        this.street = street;
        this.district = district;
        this.house = house;
        this.flat = flat;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getStreet() {
        return street;
    }

    public String getDistrict() {
        return district;
    }

    public String getHouse() {
        return house;
    }

    public Integer getFlat() {
        return flat;
    }
}
