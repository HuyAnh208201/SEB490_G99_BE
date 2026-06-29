package base.api.feature.auth.service.impl;

import base.api.shared.entity.UserModel;
import base.api.feature.auth.repository.IUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private IUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException
    {
        UserModel user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Collection<SimpleGrantedAuthority> authorities = user.getRole() != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                : new ArrayList<>();

        return new User(user.getUserName(), user.getPassword(), authorities);
    }
}
