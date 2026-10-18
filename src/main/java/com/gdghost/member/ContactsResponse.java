package com.gdghost.member;

import java.util.List;

import com.gdghost.member.MyProfileResponse.Contact;

public record ContactsResponse(List<Contact> contacts) {
}
