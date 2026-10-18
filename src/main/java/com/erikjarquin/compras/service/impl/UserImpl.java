package com.erikjarquin.compras.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.erikjarquin.compras.exceptions.UserException;
import com.erikjarquin.compras.mapper.UserMapper;
import com.erikjarquin.compras.model.dto.User.CreateUserRequest;
import com.erikjarquin.compras.model.dto.User.UpdateUserRequest;
import com.erikjarquin.compras.model.dto.User.UserDto;
import com.erikjarquin.compras.model.entity.RoleEntity;
import com.erikjarquin.compras.model.entity.UserEntity;
import com.erikjarquin.compras.repository.RoleRepository;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.UserService;
import com.erikjarquin.compras.util.InputValidator;

/**
 * Implementación de usuarios. Reglas de negocio:
 *  - Las contraseñas SIEMPRE se guardan con BCrypt (nunca en texto plano).
 *  - Un usuario no se borra físicamente: se desactiva (active=false) para
 *    conservar la integridad referencial con ventas/pagos históricos.
 *  - El email debe ser único en el sistema.
 *  - Nombre, correo y contraseña son OBLIGATORIOS y se validan
 *    en el alta **y** en la edición. Antes la edición copiaba el texto crudo y
 *    lo único que frenaba un campo vacío era el NOT NULL de la base, que
 *    respondía con un 409 diciendo "el dato ya existe".
 */
@Service
public class UserImpl implements UserService {
    /**
     * Longitud mínima de la contraseña al dar de alta un usuario.
     *
     * Es la misma que exigen los formularios del frontend. Si se cambia en
     * uno, hay que cambiarla en el otro: el backend es el que de verdad protege
     * el dato.
     */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository repository;
     private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserImpl(UserRepository repository, 
        PasswordEncoder passwordEncoder,
        RoleRepository roleRepository){
        this.repository=repository;
        this.passwordEncoder=passwordEncoder;
        this.roleRepository=roleRepository;
    }

    //Ver usuarios
   @Override
    public List<UserDto> getAllUsers() {
        return repository.findAll().stream().map(UserMapper::toDto).toList();
    }

    //Crear usuario
    @Override
    public UserDto createUser(CreateUserRequest request){
        // Primero se VALIDA y se NORMALIZA, y solo después se busca el
        // duplicado. Antes el findByEmail usaba el correo crudo y venía antes de
        // cualquier validación: dos consecuencias malas. Una, un correo vacío
        // llegaba al INSERT y la base lo rechazaba con un 409 que decía "el dato
        // ya existe", que no era lo que pasaba. Dos, el duplicado se comparaba
        // sin normalizar, así que " Ana@X.com " no se veía duplicado de
        // "ana@x.com" y el UNIQUE de la base saltaba después, con el mismo
        // mensaje engañoso.
        String emailUser = InputValidator.email(
                InputValidator.requerido(request.getEmail(), "correo"));

        //El correo no debe estar ya registrado (sería violación de unique).
        if(repository.findByEmail(emailUser).isPresent()){
            throw new UserException("El correo ya está registrado", org.springframework.http.HttpStatus.CONFLICT);
        }

        UserEntity user = new UserEntity();

        String userName = InputValidator.texto(
                InputValidator.requerido(request.getName(), "nombre"), "nombre del usuario", 50);

        // V7: la contraseña ahora es obligatoria y con longitud mínima. Antes se
        // hasheaba tal cual, y BCrypt acepta la cadena vacía: el usuario quedaba
        // creado con una contraseña que NUNCA iba a poder usar para entrar.
        String password = InputValidator.password(request.getPassword(), "contraseña", MIN_PASSWORD_LENGTH);

        user.setName(userName);
        user.setEmail(emailUser);
        user.setPassword(passwordEncoder.encode(password));

        RoleEntity role = roleRepository.findById(request.getRoleId()).orElseThrow(() ->
            new IllegalArgumentException("Rol no encontrado"));
        user.setRole(role);
        user.setActive(true);
        //"Dado de alta" = el momento en que se creó la cuenta. Es la fecha
        //que responde "¿desde cuándo trabaja con nosotros?".
        user.setActivatedAt(LocalDateTime.now());
        user.setDeactivatedAt(null);

        UserEntity saved = repository.save(user);

        return UserMapper.toDto(saved);
    }

    //Actualizar usuario
    @Override
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        //Editar un usuario no cambia la contraseña
        if(request.getPassword() != null && !request.getPassword().isBlank()){
            throw new UserException("La contraseña no se cambia al editar un usuario.", HttpStatus.BAD_REQUEST);
        }
        
        UserEntity user = repository.findById(id).orElseThrow(() ->
            new UserException("Usuario no encontrado"));

        // Este método copiaba el texto CRUDO del request."name", "correo",
        // un nombre de 500 caracteres o un correo con mayúsculas y espacios se
        // guardaban tal cual, y lo único que frenaba un campo vacío era el NOT
        // NULL de la base. Ahora pasa por los mismos validadores que el alta.
        String userName = InputValidator.texto(
                InputValidator.requerido(request.getName(), "nombre"), "nombre del usuario", 50);
        String emailUser = InputValidator.email(
                InputValidator.requerido(request.getEmail(), "correo"));

        // Duplicado de correo, pero **excluyendo al propio usuario**: si no, al
        // editar sin cambiar el correo saltaría el conflicto consigo mismo.
        repository.findByEmail(emailUser)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new UserException("El correo ya está registrado",
                            HttpStatus.CONFLICT);
                });

        user.setName(userName);
        user.setEmail(emailUser);

        RoleEntity role = roleRepository.findById(request.getRoleId()).orElseThrow(() ->
            new IllegalArgumentException("Rol no encontrado"));
        user.setRole(role);
        UserEntity updated = repository.save(user);

        return UserMapper.toDto(updated);
    }

    //Desactivar usuario
    @Override
    public void deactivateUser(Long id) {
        //Nadie se da de baja a si mismo
        UserEntity userActual = currentUserOrNull();
        
        if(userActual != null && userActual.getId().equals(id)){
            throw new UserException("No puedes darte de baja a ti mismo. Pide que otro administrador lo haga.", HttpStatus.CONFLICT);
        }
        
        
        UserEntity user = repository.findById(id).orElseThrow(() ->
            new UserException("Usuario no encontrado"));

        //Si ya estaba dado de baja no se pisa la fecha: se conserva la baja REAL
        //(la primera), que es la que responde "¿desde cuándo se fue?". Si se
        //sobrescribiera con cada llamada, al repetir la operación se perdería el
        //dato original.
        if(user.isActive()){
            user.setDeactivatedAt(LocalDateTime.now());
        }

        //Al reactivarse después, la fecha de baja deja de ser la vigente: se
        //limpia para que el filtro "dados de baja por fecha" no lo encuentre.
        user.setActivatedAt(null);
        user.setActive(false);
        repository.save(user);
    }

    //Activar usuario (y re-activar uno que estaba dado de baja)
    @Override
    public void activateUser(Long id){
        UserEntity user = repository.findById(id).orElseThrow(() ->
            new UserException("Usuario no encontrado"));

        //Siempre se refresca: re-activar es un evento nuevo ("volvió el día X"),
        // no una restauración del alta original.
        user.setActivatedAt(LocalDateTime.now());
        user.setDeactivatedAt(null);
        user.setActive(true);
        repository.save(user);
    }

    /**
     * Usuario autenticado se esta intentando dar de baja
     * 
     * Se lee del SecurityContext, que es donde Spring Security deja
     * al usuario del JWT ya validado. No se pide por parametro: si el cliente
     * pudiera decir quien es, el bloqueo de arriba no valdria nada.
     * 
     * El principal es un code UserEntity, no un code UserDetails.
     * Eso lo define JwtFilter, que autentica con la entidad completa
     * (rol y permisos incluidos). Por eso se lee el principal como entidad
     * y no se busca al usuario en la base: ya esta cargado, y volver a
     * consultarlo seria un SELECT extra por peticion.
     * 
     * Se copio de SaleImpl en vez de extraerse a un util compartido
     * porque este necesita un UserRepository: al sacarlo habría que
     * tocar también SaleImpl.
     *
     * Si no hay usuario autenticado devuelve null en vez de fallar:
     * es lo que hace que los tests unitarios (que no levantan el filtro de
     * seguridad) sigan funcionando sin montar nada.
     */
    private UserEntity currentUserOrNull(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if(auth == null || !auth.isAuthenticated()){
            return null;
        }

        Object principal = auth.getPrincipal();

        //Spring pone "anonymousUser" un String cuando no hya token
        if(principal instanceof UserEntity user){
            return user;
        }

        //Respaldo sin algún día el filtro cambia a un UserDetails
        if(principal instanceof UserDetails details){
            return repository.findByEmail(details.getUsername()).orElse(null);
        }

        return null;
    }
}
