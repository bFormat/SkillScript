package com.bformat.skillscript.execution; // 새로운 패키지 예시 (언어 관련)

import org.bukkit.Location;
import org.bukkit.entity.Entity; // Entity 임포트
import java.util.Optional; // Optional 사용
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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