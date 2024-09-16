package com.datn.be.controller;

import com.datn.be.dto.request.GoogleLoginRequest;
import com.datn.be.dto.request.user.LoginRequestDTO;
import com.datn.be.dto.request.user.RegisterRequestDTO;
import com.datn.be.dto.request.user.UserForgotPasswordDTO;
import com.datn.be.dto.request.user.UserRegisterRequestDTO;
import com.datn.be.dto.response.user.LoginResponse;
import com.datn.be.dto.response.user.UserResponse;
import com.datn.be.model.User;
import com.datn.be.service.SignupService;
import com.datn.be.service.UserService;
import com.datn.be.util.SecurityUtil;
import com.datn.be.util.annotation.ApiMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final SecurityUtil securityUtil;

    private final UserService userService;

    private final SignupService signupService;

    private final PasswordEncoder passwordEncoder;

    @Value("${khang.jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenExpiration;


    @PostMapping("/login")
    @ApiMessage("Login successfully")
    public ResponseEntity<?> auth(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        log.info("Login request: {}", loginRequestDTO);

        //nap input us pw vao
        UsernamePasswordAuthenticationToken authenticationToken
                = new UsernamePasswordAuthenticationToken(loginRequestDTO.getUsername(), loginRequestDTO.getPassword());
        //xac thuc nguoi dung, can viet ham loadbyusername trong UserDetailCustom
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        //set user's data
        SecurityContextHolder.getContext().setAuthentication(authentication);

        LoginResponse loginResponse = new LoginResponse();
        User currentUser = userService.findByEmail(loginRequestDTO.getUsername());


        if (currentUser != null) {

            //check xem co active tai khoan chua
            if (!currentUser.isEnabled()) {
                throw new RuntimeException("User is not enabled, please find our mail in your mail: " + loginRequestDTO.getUsername() +" and click");
            }

            LoginResponse.UserLogin userLogin = new
                    LoginResponse.UserLogin(currentUser.getId(), currentUser.getEmail(), currentUser.getName(), currentUser.getRole());
            loginResponse.setUser(userLogin);
        }

        //create access token
        String accessToken = securityUtil.createAccessToken(authentication.getName(), loginResponse);

        loginResponse.setAccessToken(accessToken);

        //create refresh token
        String refreshToken = securityUtil.createRefreshToken(loginRequestDTO.getUsername(), loginResponse);
        //update user
        userService.updateUserToken(refreshToken, loginRequestDTO.getUsername());

        //set cookies
        ResponseCookie responseCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(loginResponse);
    }

    @GetMapping("/account")
    @ApiMessage("Get account message")
    public ResponseEntity<?> getAccount() {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");

        User currentUser = userService.findByEmail(email);
        LoginResponse.UserLogin userLogin = new LoginResponse.UserLogin();

        if (currentUser != null) {
            userLogin.setId(currentUser.getId());
            userLogin.setEmail(currentUser.getEmail());
            userLogin.setName(currentUser.getName());
            userLogin.setRole(currentUser.getRole());
        }

        return ResponseEntity.ok().body(userLogin);
    }

    @GetMapping("/refresh")
    @ApiMessage("Get user by refresh token")
    public ResponseEntity<?> getRefreshToken(@CookieValue(name = "refresh_token", defaultValue = "abc") String refresh_token) {
        log.info("Call refresh_token");
        if (refresh_token.equals("abc")) {
            throw new AccessDeniedException("Ko co refresh token trong cookies");
        }
        //check valid
        Jwt decodedToken = securityUtil.checkValidRefreshToken(refresh_token);
        String email = decodedToken.getSubject();

        //check user by token and email

        User currentUser = userService.getUserByEmailAndRefreshToken(email, refresh_token);
        if (currentUser == null) {
            throw new AccessDeniedException("Truy cap khong hop le");
        }

        //tao token moi va set vo database a
        LoginResponse loginResponse = new LoginResponse();
        User currentUserDB = userService.findByEmail(email);
        if (currentUserDB != null) {
            LoginResponse.UserLogin userLogin = new
                    LoginResponse.UserLogin(currentUserDB.getId(), currentUserDB.getEmail(), currentUserDB.getName(), currentUserDB.getRole());
            loginResponse.setUser(userLogin);
        }

        //create access token
        String accessToken = securityUtil.createAccessToken(email, loginResponse);

        loginResponse.setAccessToken(accessToken);

        //create refresh token
        String newRefreshToken = securityUtil.createRefreshToken(email, loginResponse);
        //update user
        userService.updateUserToken(newRefreshToken, email);

        //set cookies
        ResponseCookie responseCookie = ResponseCookie
                .from("refresh_token", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshTokenExpiration)
                .build();

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(loginResponse);
    }

    @PostMapping("/logout")
    @ApiMessage("Logout user")
    public ResponseEntity<Void> logout() {
        String email = SecurityUtil.getCurrentUserLogin().orElse("");
        if (email.isEmpty()) {
            throw new AccessDeniedException("Access token khong hop le");
        }
        //update refresh token
        userService.updateUserToken(null, email);

        // remove refresh token cookies
        ResponseCookie deleteSpringCookies = ResponseCookie
                .from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteSpringCookies.toString())
                .body(null);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO) {
        log.info("Create user : {}", registerRequestDTO);
        UserResponse userResponse = signupService.register(registerRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }


    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestParam String email) {
        try {
            signupService.resendVerificationCode(email);
            return ResponseEntity.ok("Verification code sent");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@RequestBody GoogleLoginRequest googleLoginRequest) {
        String accessToken = googleLoginRequest.getAccessToken();
        String googleUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> googleResponse = restTemplate.exchange(googleUrl, HttpMethod.GET, entity, Map.class);

        if (googleResponse.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> userInfo = googleResponse.getBody();
            String email = (String) userInfo.get("email");
            String firstName = (String) userInfo.get("given_name");
            String lastName = (String) userInfo.get("family_name");

            User existingUser = userService.findByEmail(email);

            // Nếu user chưa tồn tại, tạo mới với mật khẩu ngẫu nhiên
            if (existingUser == null) {
                String randomPassword = UUID.randomUUID().toString();
                User newUser = User.builder()
                        .email(email)
                        .firstName(firstName)
                        .name(lastName)
                        .password(passwordEncoder.encode(randomPassword))
                        .enabled(true)  // Kích hoạt tài khoản ngay lập tức
                        .createdAt(Instant.now())
                        .build();

                userService.save(UserRegisterRequestDTO.builder()
                        .email(newUser.getEmail())
                        .password(newUser.getPassword())
                        .name(newUser.getName())
                        .build());

                existingUser = newUser;
            }
            existingUser = userService.findByEmail(existingUser.getEmail());
            // Kiểm tra tài khoản đã được kích hoạt hay chưa
            if (!existingUser.isEnabled()) {
                throw new RuntimeException("User is not enabled. Please check your email: " + email + " and click the activation link.");
            }

            // Tạo một Authentication token thủ công và set vào SecurityContext
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(existingUser.getEmail(), null);

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            // Tạo dữ liệu người dùng cho response
            LoginResponse loginResponse = new LoginResponse();
            LoginResponse.UserLogin userLogin = new LoginResponse.UserLogin(existingUser.getId(), existingUser.getEmail(), existingUser.getName(), existingUser.getRole());
            loginResponse.setUser(userLogin);

            // Tạo access token
            String accessTokenInternal = securityUtil.createAccessToken(existingUser.getEmail(), loginResponse);
            loginResponse.setAccessToken(accessTokenInternal);

            // Tạo refresh token
            String refreshToken = securityUtil.createRefreshToken(existingUser.getEmail(), loginResponse);

            // Cập nhật refresh token trong cơ sở dữ liệu cho user
            userService.updateUserToken(refreshToken, existingUser.getEmail());

            // Thiết lập cookie chứa refresh token
            ResponseCookie responseCookie = ResponseCookie.from("refresh_token", refreshToken)
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(refreshTokenExpiration)
                    .build();

            // Trả về response bao gồm access token và refresh token trong cookie
            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(loginResponse);

        } else {
            // Trường hợp token Google không hợp lệ
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google access token");
        }
    }

    @PostMapping("/forgot")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody UserForgotPasswordDTO forgotPasswordDTO) {
        signupService.forgotPassword(forgotPasswordDTO.getEmail());
        return ResponseEntity.status(HttpStatus.OK).body("Forgot Password Sent");
    }


}
