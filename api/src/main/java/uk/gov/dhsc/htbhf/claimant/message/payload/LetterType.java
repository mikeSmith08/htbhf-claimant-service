package uk.gov.dhsc.htbhf.claimant.message.payload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LetterType {

    UPDATE_YOUR_ADDRESS("518e6e56-32db-4a8c-ad7a-7baa99777f79"),
    //This is application success with children match or no children but pregnant
    APPLICATION_SUCCESS_CHILDREN_MATCH("7a344fe8-308b-432c-a0cd-406a959688a7"),
    APPLICATION_SUCCESS_CHILDREN_MISMATCH("a9cf7548-22ed-434c-b2ce-b082dfd08f56");

    private final String templateId;
}
