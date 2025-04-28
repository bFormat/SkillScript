package com.bformat.skillscript.execution; // 새로운 패키지 예시 (언어 관련)

import org.bukkit.Location;
import org.bukkit.entity.Entity; // Entity 임포트
import java.util.Optional; // Optional 사용
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import org.bukkit.attribute.Attribute; // Attribute 임포트 추가
import org.bukkit.entity.LivingEntity; // LivingEntity 임포트 추가
import org.bukkit.entity.Damageable; // Damageable 임포트 추가

import java.util.HashMap;
import java.util.Map;

/**
 * 스크립트 실행 중의 상태 정보를 관리하는 클래스.
 * 액션 간에 이 객체가 전달되어 상태를 공유하고 수정합니다.
 */
public class ExecutionContext {

    private final Player caster;
    private final Location castLocation;
    private final Vector castDirection;

    // 동적으로 변경될 수 있는 상태
    private Object currentTarget; // Entity, Location, Area 등 다양한 타입 가능
    private Object currentObject; // 스펠 오브젝트 (나중에 구현)
    private Location collisionLocation; // 충돌 위치 (OnCollision 에서 사용)
    private Object collisionTarget; // 충돌 대상 (OnCollision 에서 사용)

    private final Map<String, Object> variables = new HashMap<>(); // 스크립트 내 변수 저장

    public ExecutionContext(Player caster) {
        this.caster = caster;
        this.castLocation = caster.getLocation();
        this.castDirection = caster.getLocation().getDirection();
        this.currentTarget = caster; // 기본 타겟은 시전자 자신
    }

    // --- Getters ---
    public Player getCaster() { return caster; }
    public Location getCastLocation() { return castLocation; }
    public Vector getCastDirection() { return castDirection; }
    public Object getCurrentTarget() { return currentTarget; }
    public Object getCurrentObject() { return currentObject; }
    public Location getCollisionLocation() { return collisionLocation; }
    public Object getCollisionTarget() { return collisionTarget; }

    // --- Target 유틸리티 Getters ---
    public Entity getCurrentTargetAsEntity() {
        return (currentTarget instanceof Entity) ? (Entity) currentTarget : null;
    }
    public Location getCurrentTargetAsLocation() {
        if (currentTarget instanceof Location) {
            return (Location) currentTarget;
        } else if (currentTarget instanceof Entity) {
            return ((Entity) currentTarget).getLocation();
        }
        // TODO: Area 등 다른 타겟 타입의 위치 반환 로직 추가
        return null; // 적절한 위치를 얻을 수 없으면 null 반환
    }

    // --- Setters ---
    public void setCurrentTarget(Object currentTarget) { this.currentTarget = currentTarget; }
    public void setCurrentObject(Object currentObject) { this.currentObject = currentObject; }
    public void setCollisionLocation(Location collisionLocation) { this.collisionLocation = collisionLocation; }
    public void setCollisionTarget(Object collisionTarget) { this.collisionTarget = collisionTarget; }

    // --- Variable Management (ConcurrentHashMap handles thread safety) ---
    public void setVariable(String name, Object value) {
        if (name != null && !name.isBlank()) {
            variables.put(name.toLowerCase(), value);
        }
    }

    public Object getVariable(String name) {
        return (name != null) ? variables.get(name.toLowerCase()) : null;
    }

    public <T> T getVariableAs(String name, Class<T> type) {
        Object value = getVariable(name);
        if (type != null && type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    // --- 추가: 타입별 변수 가져오기 헬퍼 (Optional 사용) ---
    public Optional<Location> getVariableAsLocation(String name) {
        return Optional.ofNullable(getVariableAs(name, Location.class));
    }

    public Optional<Vector> getVariableAsVector(String name) {
        return Optional.ofNullable(getVariableAs(name, Vector.class));
    }

    public Optional<Entity> getVariableAsEntity(String name) {
        return Optional.ofNullable(getVariableAs(name, Entity.class));
    }

    /**
     * 식별자 문자열(변수명 또는 셀렉터)을 해석하여 숫자(Double) 값으로 반환합니다.
     * 예: "myNumericVar", "@CasterLocation.X", "@TargetHealth", "@CurrentTarget.Y"
     * 해석할 수 없거나 숫자 타입이 아니면 Optional.empty()를 반환합니다.
     *
     * @param identifier 변수명 또는 셀렉터 문자열
     * @return 해석된 숫자 값(Double) Optional
     */
    public Optional<Double> resolveNumericValue(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String lowerId = identifier.toLowerCase();

        // 1. 특수 키워드/셀렉터 파싱 (@ 기호 포함)
        if (lowerId.startsWith("@")) {
            String[] parts = lowerId.substring(1).split("\\.", 2); // "@" 제거 후 "." 기준으로 최대 2개 분리
            String baseSelector = "@" + parts[0]; // 예: @casterlocation
            String attribute = (parts.length > 1) ? parts[1] : null; // 예: x, y, z, health

            // 위치 기반 셀렉터
            if (baseSelector.equals("@casterlocation") || baseSelector.equals("@castlocation") || baseSelector.equals("@targetlocation") || baseSelector.equals("@currenttargetlocation")) {
                Optional<Location> locOpt;
                if (baseSelector.equals("@casterlocation") || baseSelector.equals("@castlocation")) {
                    locOpt = resolveLocation("@casterlocation"); // resolveLocation은 clone된 값을 반환
                } else { // target 또는 currenttarget
                    locOpt = Optional.ofNullable(getCurrentTargetAsLocation()); // 이것도 clone 필요할 수 있음. getCurrentTargetAsLocation() 확인
                }


                if (locOpt.isPresent() && attribute != null) {
                    Location loc = locOpt.get();
                    switch (attribute) {
                        case "x": return Optional.of(loc.getX());
                        case "y": return Optional.of(loc.getY());
                        case "z": return Optional.of(loc.getZ());
                        // case "yaw": return Optional.of((double) loc.getYaw());
                        // case "pitch": return Optional.of((double) loc.getPitch());
                        default: return Optional.empty(); // 알 수 없는 속성
                    }
                }
                return Optional.empty(); // 위치를 찾을 수 없거나 속성이 없음
            }
            // 엔티티 기반 셀렉터
            else if (baseSelector.equals("@caster") || baseSelector.equals("@target") || baseSelector.equals("@currenttarget")) {
                Optional<Entity> entityOpt;
                if (baseSelector.equals("@caster")) {
                    entityOpt = resolveEntity("@caster");
                } else {
                    entityOpt = Optional.ofNullable(getCurrentTargetAsEntity());
                }

                if (entityOpt.isPresent() && attribute != null) {
                    Entity entity = entityOpt.get();
                    switch (attribute) {
                        case "x": return Optional.of(entity.getLocation().getX());
                        case "y": return Optional.of(entity.getLocation().getY());
                        case "z": return Optional.of(entity.getLocation().getZ());
                        case "yaw": return Optional.of((double) entity.getLocation().getYaw());
                        case "pitch": return Optional.of((double) entity.getLocation().getPitch());
                        case "health":
                            if (entity instanceof Damageable) {
                                return Optional.of(((Damageable) entity).getHealth());
                            }
                            return Optional.empty(); // Damageable 아님
                        case "maxhealth":
                            if (entity instanceof LivingEntity) { // LivingEntity가 Attribute.GENERIC_MAX_HEALTH 를 가짐
                                LivingEntity le = (LivingEntity) entity;
                                return Optional.ofNullable(le.getAttribute(Attribute.MAX_HEALTH))
                                        .map(attr -> attr.getValue());
                            }
                            // Player도 가능하지만 LivingEntity로 커버됨
                            return Optional.empty(); // LivingEntity 아님
                        // TODO: 다른 엔티티 속성 추가 (예: foodlevel for player)
                        default: return Optional.empty(); // 알 수 없는 속성
                    }
                }
                return Optional.empty(); // 엔티티를 찾을 수 없거나 속성이 없음
            }
            // TODO: 다른 타입의 셀렉터 (@CastDirection.X 등) 추가

            return Optional.empty(); // 알려지지 않은 @ 셀렉터
        }
        // 2. 일반 변수 파싱 (@ 기호 없음)
        else {
            Object variableValue = getVariable(identifier); // getVariable은 이미 소문자 처리
            if (variableValue instanceof Number) {
                return Optional.of(((Number) variableValue).doubleValue());
            } else if (variableValue instanceof String) { // 문자열 숫자도 변환 시도
                try {
                    return Optional.of(Double.parseDouble((String) variableValue));
                } catch (NumberFormatException e) {
                    return Optional.empty(); // 숫자로 변환 불가
                }
            }
            // TODO: Location, Vector 등 다른 타입에서 특정 숫자 값을 추출할지 결정 (예: myLocationVar.X)
            // 현재는 변수 자체가 Number 타입이거나 숫자 문자열인 경우만 처리
        }

        return Optional.empty(); // 어떤 경우에도 해당하지 않음
    }

    /**
     * 변수 또는 특수 키워드로부터 Location을 해석합니다.
     * 예: "myLocationVar", "@CasterLocation", "@TargetLocation"
     * @param identifier 변수명 또는 키워드
     * @return 해석된 Location Optional
     */
    public Optional<Location> resolveLocation(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String lowerId = identifier.toLowerCase();
        // System.out.println("DEBUG: Resolving location for identifier: " + identifier); // 로그 추가
        switch (lowerId) {
            case "@casterlocation":
            case "@castlocation":
                // System.out.println("DEBUG: Resolved as @CasterLocation"); // 로그 추가
                return Optional.ofNullable(getCaster()).map(Player::getLocation).map(Location::clone);
            // ... 다른 케이스 로그 추가 ...
            default:
                // System.out.println("DEBUG: Trying to resolve as variable: " + identifier); // 로그 추가
                Optional<Location> locOpt = getVariableAsLocation(identifier);
                // System.out.println("DEBUG: Variable lookup result: " + (locOpt.isPresent() ? locOpt.get() : "Not Found")); // 로그 추가
                return locOpt;
        }
    }

    /**
     * 변수 또는 특수 키워드로부터 Vector (주로 방향)를 해석합니다.
     * 예: "myDirectionVar", "@CastDirection", "@TargetDirection"
     * @param identifier 변수명 또는 키워드
     * @return 해석된 Vector Optional
     */
    public Optional<Vector> resolveVector(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();

        String lowerId = identifier.toLowerCase();
        switch(lowerId) {
            case "@castdirection": // 시전 시점 방향
                return Optional.of(getCastDirection()); // Context 내부에서 clone 처리
            case "@casterdirection": // 현재 캐스터 방향
                return Optional.ofNullable(getCaster()).map(p -> p.getLocation().getDirection().clone());
            case "@targetdirection":
                Entity targetEntity = getCurrentTargetAsEntity();
                if (targetEntity != null) {
                    return Optional.of(targetEntity.getLocation().getDirection().clone());
                }
                return Optional.empty(); // 대상이 엔티티가 아니면 방향 없음
            default:
                return getVariableAsVector(identifier);
        }
    }

    /**
     * 변수 또는 특수 키워드로부터 Entity를 해석합니다.
     * 예: "myTargetVar", "@Caster", "@CurrentTarget"
     * @param identifier 변수명 또는 키워드
     * @return 해석된 Entity Optional
     */
    public Optional<Entity> resolveEntity(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();

        String lowerId = identifier.toLowerCase();
        switch(lowerId) {
            case "@caster":
                return Optional.ofNullable(getCaster());
            case "@currenttarget":
            case "@target": // Alias
                return Optional.ofNullable(getCurrentTargetAsEntity());
            default:
                return getVariableAsEntity(identifier);
        }
    }

    public Map<String, Object> getVariables() {
        return variables; // 전체 변수 맵 반환 (읽기 전용으로 사용 권장)
    }

    // TODO: 필요에 따라 더 많은 상태 및 유틸리티 메소드 추가
}