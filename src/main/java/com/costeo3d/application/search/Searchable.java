package com.costeo3d.application.search;

import java.util.List;

public interface Searchable<T> {

    List<T> search(String query);
}