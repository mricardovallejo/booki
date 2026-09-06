package com.booki.service.impl;

import com.booki.domain.ReaderProfile;
import com.booki.domain.User;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the editable generic reader profile every account starts with. */
@Component
@RequiredArgsConstructor
public class ReaderProfileProvisioner {

    static final String DEFAULT_NAME = "My reader profile";
    static final String FALLBACK_CONTEXT =
            "My goal for this reading: \n"
                    + "What I already know about the topic: \n"
                    + "How I like to learn (examples, definitions, pace): \n"
                    + "Anything that helps me (short paragraphs, dyslexia-friendly formatting, ...): ";

    private final ReaderProfileRepository readerProfiles;
    private final UserRepository users;

    /** Creates the owned default profile for a newly registered account. */
    @Transactional
    public ReaderProfile provisionFor(User user) {
        return createFromGeneralTemplate(user);
    }

    private ReaderProfile createFromGeneralTemplate(User user) {
        ReaderProfile template = readerProfiles
                .findFirstByUserIsNullAndDefaultProfileTrueOrderByIdAsc()
                .orElse(null);

        ReaderProfile profile = new ReaderProfile();
        profile.setUser(user);
        profile.setName(DEFAULT_NAME);
        profile.setContext(template != null ? template.getContext() : FALLBACK_CONTEXT);
        profile.setReaderLevel(template != null ? template.getReaderLevel() : null);
        profile.setDefaultProfile(false);
        profile.setReadOnly(false);

        ReaderProfile saved = readerProfiles.save(profile);
        user.setDefaultReaderProfile(saved);
        users.save(user);
        return saved;
    }
}
