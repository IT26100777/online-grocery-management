package com.grocery.onlinegrocerymanagement.model;

public class Category {
    private int id;
    private String name;
    private String description;

    //default constructor
    public Category(){
    }

    //parameterized constructor
    public Category(int id, String name, String description){
        this.id = id;
        this.name = name;
        this.description = description;
    }

    //getters and setters
    public int getId(){
        return id;
    }

    public void setId(int id){
        this.id = id;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getDescription(){
        return description;
    }

    public void setDescription(String description){
        this.description = description;
    }
}
