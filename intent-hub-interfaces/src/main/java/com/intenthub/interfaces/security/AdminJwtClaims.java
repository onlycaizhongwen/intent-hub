package com.intenthub.interfaces.security;

import java.util.List;

record AdminJwtClaims(String actor, List<String> roles) {
}
