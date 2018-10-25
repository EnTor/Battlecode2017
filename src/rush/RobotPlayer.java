package rush;

import battlecode.common.*;
import static java.lang.Math.*;

import java.util.TreeMap;

public class RobotPlayer {
	private static RobotController rc;
	private static RobotType type;
	private static Team enemyTeam = Team.B;
	private static final float PI = (float) Math.PI;
	
	private static final int BULLET_MARGIN = 10; // how many bullets that are required to be in the bank after a robot is queued up
	private static final int ARCHON_TIMEOUT = 80;
	private static final Direction[] dirList = new Direction[8];

	//Movement
	private static float moveLength;
	private static MapLocation currentLocation;
	private static MapLocation targetLocation;
	private static Direction targetDirection;
	private static Direction casualDirection;
	
	//Scanning
	private static RobotInfo[] enemyRobots;
	private static RobotInfo[] friendlyRobots;
	private static TreeInfo[] trees;
	private static BulletInfo[] bullets;
	
	//Build Counts
	private static final int GARDENER_MAX_COUNT = 1;
	private static final int INITIAL_SCOUT_BUILD_COUNT = 2;
	private static final int INITIAL_LUMBERJACK_MAX_BUILD_COUNT = 7;

	// Scout
	private static int superTreeID = -1;
	private static boolean hasEnemyTarget = false;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		
		casualDirection = rc.getLocation().directionTo(rc.getInitialArchonLocations(enemyTeam)[0]);
		type = rc.getType();
		moveLength = type.strideRadius;
		
		for (int i = 0; i < dirList.length; i++) {
			dirList[i] = new Direction(2 * PI / i);
		}
		
		switch (type) {
			case ARCHON:
				runArchon();
				break;
			case GARDENER:
				int gardenersInQueue = rc.readBroadcast(5) - 1;
				rc.broadcast(5, gardenersInQueue >= 0 ? gardenersInQueue : 0);
				runGardener();
				break;
			case SOLDIER:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runSoldier();
				break;
			case LUMBERJACK:
				runLumberjack();
				break;
			case SCOUT:
				targetLocation = rc.getInitialArchonLocations(enemyTeam)[0];
				targetDirection = rc.getLocation().directionTo(targetLocation);
				runScout();
				break;
			case TANK:
				runTank();
				break;
		}
	}

	private static void runArchon() throws GameActionException {
		//Make out last archon
		boolean isInformer = false;
		int nrFormerArchons = rc.readBroadcast(6) + 1;
		rc.broadcast(6, nrFormerArchons);
		
		//Search if starting area holds lots of trees
		TreeInfo[] earlyTrees = rc.senseNearbyTrees(-1);
		rc.broadcast(9, min(max(rc.readBroadcast(9), earlyTrees.length / 4), INITIAL_LUMBERJACK_MAX_BUILD_COUNT));
		
		while (true) {
			try {
				currentLocation = rc.getLocation();
				intelligentMove(randomDirection());
				
				//clear old enemy archon channels
				clearEnemyArchonChannels();
				 
				//Need channel 6 to make out the final archon
				if (rc.getRoundNum() != 1 && nrFormerArchons == rc.readBroadcast(6)) {
					isInformer = true;
				}

				int inGardenerCount = rc.readBroadcast(3);
				if (rc.isBuildReady() && inGardenerCount + rc.readBroadcast(5) < GARDENER_MAX_COUNT) {
					if (tryBuildRobot(RobotType.GARDENER))
						rc.broadcast(5, rc.readBroadcast(5) + 1);
				}
				rc.broadcast(4, inGardenerCount);
				if (isInformer) {
					rc.broadcast(3, 0);
				}
	
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runGardener() throws GameActionException {
		while (true) {
			try {
				currentLocation = rc.getLocation();
				intelligentMove(randomDirection());		
				// Build Scout
				if (rc.readBroadcast(8) < INITIAL_SCOUT_BUILD_COUNT + rc.getRoundNum() / 600 && rc.isBuildReady()) {
					if(tryBuildRobot(RobotType.SCOUT)){
						rc.broadcast(8, rc.readBroadcast(8) + 1);
					}
				}
				//Build Lumberjack
				if (rc.readBroadcast(7) < rc.readBroadcast(9) && rc.isBuildReady() ) {
					if(tryBuildRobot(RobotType.LUMBERJACK)){
						rc.broadcast(7, rc.readBroadcast(7) + 1);
					}
				}
				// Build Soldier
				if (rc.isBuildReady() && rc.readBroadcast(4) + rc.readBroadcast(5) >= GARDENER_MAX_COUNT) {
					tryBuildRobot(RobotType.SOLDIER);
				}
				rc.broadcast(3, rc.readBroadcast(3) + 1);
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Gardener Exception");
				e.printStackTrace();
			}
		}
	}

	//TODO don't prioritize archon
	@SuppressWarnings("static-access")
	private static void runSoldier() throws GameActionException {
		while (true) {
			try {
				currentLocation = rc.getLocation();
				
				//Check for enemies and move accordingly
				enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
				if (enemyRobots.length > 0) {
					TreeMap<Float, BodyInfo> sortedRobots = sortByDistance(enemyRobots);
		            RobotInfo enemyRobot = (RobotInfo) sortedRobots.pollFirstEntry().getValue();
		            targetLocation = enemyRobot.getLocation();
		            targetDirection = currentLocation.directionTo(targetLocation);
		    		
		            //Make sure no friendly bros or tree bros are in the line of fire
		            if(noFriendlyFire() && noTreeFire() && rc.canFireTriadShot()){
						rc.fireTriadShot(currentLocation.directionTo(targetLocation));
					}
	                
	                if(enemyRobot.getType() != type.GARDENER && enemyRobot.getType() != type.ARCHON){
			            if(currentLocation.distanceTo(targetLocation) <= 4f){
			            	intelligentMove(targetDirection.opposite());
			            } else {
				            //So that this bro doesn't walk into his own shots
				            if(!rc.hasAttacked()){
				            	intelligentMove(targetDirection);
				            } else if((bullets = rc.senseNearbyBullets(8)).length > 0){
				            	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(8), 2));
				            }
			            }
	                } else{
	                	if(enemyRobot.getType() == type.ARCHON){
	                		compromiseArchon(enemyRobot);
	                	}
	                	if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
	                		bullets = rc.senseNearbyBullets(8);
	                		if(bullets.length > 0){
	                			tryMove(advancedBulletDodge(targetDirection, bullets, 2));
	                		}
			            } else if(!rc.hasAttacked()){
			            	intelligentMove(targetDirection);
					    } else if((bullets = rc.senseNearbyBullets(8)).length > 0){
					    	tryMove(advancedBulletDodge(targetDirection, rc.senseNearbyBullets(8), 2));
					    }
	                }
	                //No enemy found -> go to enemy archon locations
	             } else {
	            	 boolean hasTarget = false;
	            	 for(int i = 11; i < 23; i += 4){
	            		 if(rc.readBroadcast(i) != 0){
	            			 targetDirection = currentLocation.directionTo(new MapLocation(rc.readBroadcastFloat(i + 1), rc.readBroadcastFloat(i + 2)));
	            			 hasTarget = true;
	            			 break;
	            		 }
	            	 }
	            	 if(hasTarget){
	            		 intelligentMove(targetDirection);
	            	 } else {
	            		 bounce();
	            		 intelligentMove(casualDirection);
	            	 }
	             }
				Clock.yield();
			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}
		}

	}

	private static void runLumberjack() throws GameActionException {
		while (true) {
			 try {
				 currentLocation = rc.getLocation();
					
				 enemyRobots = rc.senseNearbyRobots(5, enemyTeam);
	             trees = rc.senseNearbyTrees(-1);
	             if (enemyRobots.length > 0) {
	            	 TreeMap<Float, BodyInfo> sortedRobots = sortByDistance(enemyRobots);
		             RobotInfo enemyRobot = (RobotInfo) sortedRobots.pollFirstEntry().getValue();
		             targetLocation = enemyRobot.getLocation();
		             targetDirection = currentLocation.directionTo(targetLocation);
		             if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
		            	 tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.01f);
		             } else {
		            	 tryMove(targetDirection, 20, 3, type.strideRadius);
		             } 
		             if (currentLocation.distanceTo(targetLocation) < GameConstants.LUMBERJACK_STRIKE_RADIUS && rc.canStrike()) {
		            	 rc.strike();
		            }
	             } else if(trees.length > 0){ 
		             TreeMap<Float, BodyInfo> sortedTrees = sortByDistance(trees);
		             TreeInfo tree = (TreeInfo) sortedTrees.pollFirstEntry().getValue();
		             targetLocation = tree.getLocation();
		             targetDirection = currentLocation.directionTo(targetLocation);
		             if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
		            	 tryMove(targetDirection, 20, 3, currentLocation.distanceTo(targetLocation) - type.bodyRadius - tree.getRadius() - 0.01f);
		             } else {
		            	 tryMove(targetDirection, 20, 3, type.strideRadius);
		             }      
		             if (rc.canChop(tree.getID())) {
		            	 rc.chop(tree.getID());
		             }
	             } else {
	            	 bounce();
	            	 intelligentMove(casualDirection);
	             }
	             Clock.yield();
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
		}
	}
	
	private static void runScout() throws GameActionException{
		while (true) {
			try {
				currentLocation = rc.getLocation();		
				
				//clear old enemy archon channels
				clearEnemyArchonChannels();
				
				hasEnemyTarget = huntEnemies(currentLocation);
				//Check for gardeners, archons and trees containing bullets
				if(!hasEnemyTarget){
					if(superTreeID == -1){
						//If no gardeners were found, look for trees containing bullets
						findTreeWithBullets();
						
						//if superIsSet = true look for the nearby superTreeID
					} else if((superTreeID != -1 && rc.canInteractWithTree(superTreeID))){
						if(rc.canShake() && rc.senseTree(superTreeID).getContainedBullets() > 0){
							rc.shake(superTreeID);
						}
						if(!findTreeWithBullets()){
							superTreeID = -1;
						}
					}
				}
				//Move regularly or to target
				if(!hasEnemyTarget && superTreeID == -1){
					//bounce
					bounce();
					intelligentMove(casualDirection);
				} else {
					tryMove(targetDirection, 20, 3, moveLength);
				}
				
				if(hasEnemyTarget && noFriendlyFire() && currentLocation.distanceTo(targetLocation) < 4f && rc.canFireSingleShot()){
					rc.fireSingleShot(targetDirection);
				}
				
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
		
	private static void runTank() throws GameActionException{
		while (true) {
			try {
				currentLocation = rc.getLocation();
				Clock.yield();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	

	//---------------------SCOUT METHODS---------------------//
	
	@SuppressWarnings("static-access")
	private static boolean huntEnemies(MapLocation currentLocation) throws GameActionException{
		enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
		TreeMap<Float, BodyInfo> sortedEnemyRobots = sortByDistance(enemyRobots);
		for(int i = 0; i < enemyRobots.length; i++){
			RobotInfo enemyRobot = (RobotInfo) sortedEnemyRobots.pollFirstEntry().getValue();
			if(enemyRobot.getType() == type.GARDENER){
				hugTarget(enemyRobot);
				return true;
			} else if (enemyRobot.getType() == type.ARCHON) {
				//broadcast enemy archons' locations
				compromiseArchon(enemyRobot);
				return false;
			}
		}
		return false;
	}
	
	private static void hugTarget(RobotInfo enemyRobot){
		targetLocation = enemyRobot.getLocation();
		targetDirection = new Direction(currentLocation, targetLocation);
		if(currentLocation.distanceTo(targetLocation) < type.strideRadius){
			moveLength = currentLocation.distanceTo(targetLocation) - type.bodyRadius - enemyRobot.getType().bodyRadius - 0.05f;
		} else {
			moveLength = type.strideRadius;
		}
		superTreeID = -1;
	}
	
	private static boolean findTreeWithBullets(){
		try {
			trees = rc.senseNearbyTrees(-1);
			for(TreeInfo tree: trees){
				if(tree.getContainedBullets() > 0){
					int treeID = tree.getID();
					if(rc.canShake() && rc.canInteractWithTree(treeID)){
						rc.shake(treeID);
					} else {
					targetLocation = tree.getLocation();
					targetDirection = new Direction(currentLocation, targetLocation);
					superTreeID = treeID;
					moveLength = type.strideRadius;
					}
					return true;
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}	
		return false;
	}
	
	//---------------------END OF SCOUT METHODS---------------------//

	//---------------------MISC---------------------//
	
	
	//Recks enemy Archons xD
	private static void compromiseArchon(RobotInfo enemyRobot){
		try {
			int archonID = enemyRobot.getID();
			int j = 10;
			while(archonID != rc.readBroadcast(j) && rc.readBroadcast(j) != 0){
				j += 4;
			}
			if(rc.readBroadcast(j) == 0){
				rc.broadcast(j, archonID);
			}
			
			rc.broadcast(j + 1, rc.getRoundNum());
			rc.broadcastFloat(j + 2, enemyRobot.getLocation().x);
			rc.broadcastFloat(j + 3, enemyRobot.getLocation().y);
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	private static void clearEnemyArchonChannels(){
		try {
			for(int i = 11; i < 23; i += 4){
				if(rc.readBroadcast(i) != 0 && rc.getRoundNum() - rc.readBroadcast(i) > ARCHON_TIMEOUT){
					rc.broadcast(i - 1, 0);
					rc.broadcast(i, 0);
					rc.broadcast(i + 1, 0);
					rc.broadcast(i + 2, 0);
				}
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	//---------------------END MISC---------------------//
	
	//---------------------BUILDING---------------------//
	
	private static boolean tryBuildRobot(RobotType type) throws GameActionException {
		if (rc.getTeamBullets() > type.bulletCost + BULLET_MARGIN + rc.getRoundNum() / 25) {
			for (int i = 0; i < dirList.length; i++) {
				if (rc.canBuildRobot(type, dirList[i])) {
					rc.buildRobot(type, dirList[i]);
					return true;
				}
			}
		}
		return false;
	}
	
	//---------------------END BUILDING---------------------//

	//---------------------MOVEMENT---------------------//	
	
	private static void bounce(){
		if (!rc.canMove(casualDirection)) {
			float dx = casualDirection.getDeltaX(2.5f);
			if (!rc.canMove(new Direction(dx, 0), abs(dx))) {
				if (casualDirection.radians > 0) {
					casualDirection = new Direction(PI - casualDirection.radians);
				} else {
					casualDirection = new Direction(-PI + casualDirection.radians);
				}
			}
			float dy = casualDirection.getDeltaY(2.5f);
			if (!rc.canMove(new Direction(0, dy), abs(dy))) {
				casualDirection = new Direction(casualDirection.radians * -1);
			}
		}
	}
	
	private static Direction randomDirection() {
		return new Direction((float) random() * 2 * PI);
	}

	private static void intelligentMove(Direction dir) {
		try {
			bullets = rc.senseNearbyBullets(8);
			if (bullets.length > 0) {
				dir = advancedBulletDodge(dir, bullets, 0);
			}
			tryMove(dir, 20, 3, type.strideRadius);
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}
	
	private static void intelligentMove(MapLocation loc) {
			intelligentMove(currentLocation.directionTo(loc));
	}

	//No bulletdodge here, so that they don't run into their own shots
	private static void intelligentMove(Direction dir, float moveLength){
		try {
			bullets = rc.senseNearbyBullets(8);
			if (bullets.length > 0) {
				tryMove(advancedBulletDodge(dir, bullets, 0), 20, 3, type.strideRadius);
			} else {
				tryMove(dir, 20, 3, moveLength);
			}
		} catch (GameActionException e) {
			e.printStackTrace();
		}
	}

	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	private static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide, float moveLength) throws GameActionException {

		// First, try intended direction
		if (rc.canMove(dir, moveLength)) {
			rc.move(dir, moveLength);
			return true;
		}

		// Now try a bunch of similar angles
		int currentCheck = 1;

		while (currentCheck <= checksPerSide) {
			// Try the offset of the left side
			if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// Try the offset on the right side
			if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength)) {
				rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck), moveLength);
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}
	
	//---------------------END MOVEMENT---------------------//
	
	//---------------------BULLET DODGING---------------------//
	
	/*
	 * Don't use this shiet for scout, they have to great bullet detection and will just act weird
	 * checks  x x x
	 * 		  x -O- x
	 *         x   x, picks the location of the 7 that is safe
	 */
	private static Direction advancedBulletDodge(Direction dir, BulletInfo[] bullets, int goForward) {
		Direction potentialDirection = dir;
		TreeMap<Integer, Direction> weights = new TreeMap<>();
		int weight;
		for(int i = goForward; i < 4; i = i > 0 ? i*-1 : --i*-1){
			weight = 0;
			potentialDirection = dir.rotateLeftRads(i*PI/4);
			System.out.println("potentialDirection: " + potentialDirection.radians);
			if(!rc.canMove(potentialDirection)){
				continue;
			}
			for(BulletInfo bullet: bullets){
				MapLocation potentialLoc = currentLocation.add(potentialDirection, type.strideRadius);
				Direction bulletDir = bullet.getDir();
				MapLocation bulletLoc = bullet.getLocation();
	
				Direction fromBulletDir = bulletLoc.directionTo(potentialLoc);
				float distance = bulletLoc.distanceTo(potentialLoc);
				float hitAngle = bulletDir.radiansBetween(fromBulletDir);
	
				if (abs(hitAngle) > PI / 2) {
					continue;
				}
	
				float perpendicularDistance = (float) abs(distance * sin(hitAngle));
				if (perpendicularDistance <= type.bodyRadius) {
					if(potentialLoc.distanceTo(bulletLoc) < 5){
						weight += 2;
					} else {
						weight++;
					}
				}
			}
			if(weight == 0){
				System.out.println("0 weight: " + potentialDirection.radians);
				return potentialDirection;
			}
			weights.put(weight, potentialDirection);
		}
		System.out.println("return potentialDirection: " + potentialDirection.radians);
		if(!weights.isEmpty()){
			return weights.pollFirstEntry().getValue();
		}
		return dir;
	}

	private static Direction bulletDodge(Direction dir, BulletInfo[] bullets) {
		TreeMap<Float, BodyInfo> bulletMap = sortByDistance(bullets);
		for (int i = 0; i < bulletMap.size(); i++) {
			BulletInfo bullet = (BulletInfo) bulletMap.pollFirstEntry().getValue();
			Direction bDir = bullet.getDir();
			MapLocation bLoc = bullet.getLocation();

			Direction fromBulletDir = bLoc.directionTo(currentLocation);
			float distance = bLoc.distanceTo(currentLocation);
			float hitAngle = bDir.radiansBetween(fromBulletDir);
			if (abs(hitAngle) > PI / 2) {
				continue;
			}

			float perpendicularDistance = (float) abs(distance * sin(hitAngle));

			// if true then bullet will collide -> we must dodge
			if (perpendicularDistance <= type.bodyRadius) {
				Direction newDir = bullet.getDir().rotateLeftRads(PI / 2);
				if (rc.canMove(newDir)) {
					return newDir;
				} else if (rc.canMove(newDir.rotateLeftRads(PI))) {
					return newDir.rotateLeftRads(PI);
				}
			}
		}
		return dir;
	}
	
	//---------------------DODGING---------------------//
	
	//---------------------SHOOTING---------------------//
	
	//Not 100 % this works, but I think so
	private static boolean noFriendlyFire(){
        friendlyRobots = rc.senseNearbyRobots(4, rc.getTeam());
		for(RobotInfo friendlyRobot: friendlyRobots){
			MapLocation friendlyLocation = friendlyRobot.getLocation();
			float distanceToFriendly = currentLocation.distanceTo(friendlyLocation);
			float criticalAngle = abs(currentLocation.directionTo(friendlyLocation).radiansBetween(targetDirection));
			float criticalDistance = (float) (distanceToFriendly * sin(criticalAngle));
			if(criticalDistance < friendlyRobot.getRadius() + 0.5f && distanceToFriendly < currentLocation.distanceTo(targetLocation)){
				return false;
			}
		}
		return true;
	}
	
	private static boolean noTreeFire(){
        trees = rc.senseNearbyTrees(6);
		for(TreeInfo tree: trees){
			MapLocation treeLocation = tree.getLocation();
			float distanceToTree = currentLocation.distanceTo(treeLocation);
			float criticalAngle = abs(currentLocation.directionTo(treeLocation).radiansBetween(targetDirection));
			float criticalDistance = (float) (distanceToTree * sin(criticalAngle));
			if(criticalDistance < tree.getRadius() + 0.02f && distanceToTree < currentLocation.distanceTo(targetLocation)){
				return false;
			}
		}
		return true;
	}
	
	//---------------------END SHOOTING---------------------//
	
	//---------------------SORTING---------------------//

	private static TreeMap<Float, BodyInfo> sortByDistance(BodyInfo[] bodies) {
		TreeMap<Float, BodyInfo> bodyMap = new TreeMap<>();
		for (BodyInfo body : bodies) {
			bodyMap.put(rc.getLocation().distanceTo(body.getLocation()), body);
		}
		return bodyMap;
	}
	
	//---------------------END SORTING---------------------//
}
