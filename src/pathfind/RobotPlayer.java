package pathfind;

import battlecode.common.*;
import static java.lang.Math.*;

import java.util.Map;
import java.util.TreeMap;

public class RobotPlayer {
	static Team enemyTeam = Team.B;
	static RobotController rc;
	static MapLocation historyLocationCheck[] = new MapLocation[3];
	static MapLocation historyLocations[] = new MapLocation[7];
	static Direction historyDirectionCheck;
	static Direction historyDirection;

	public static void run(RobotController rcIn) throws GameActionException {
		rc = rcIn;
		if (rc.getTeam() == Team.B) {
			enemyTeam = Team.A;
		}
		switch (rc.getType()) {
		case ARCHON:
			runArchon();
			break;
		case GARDENER:
			runGardener();
			break;
		case SOLDIER:
			runSoldier();
			break;
		case LUMBERJACK:
			runLumberjack();
			break;
		}
	}

	private static void runArchon() throws GameActionException {
		rc.hireGardener(new Direction(0f)); 
		while (true) {
//			try {
//				TreeInfo[] ti = rc.senseNearbyTrees(4);
//				pathfind(new MapLocation(25f, 5f), ti);
			Clock.yield();
//			} catch (GameActionException e) {
//				System.out.println("Archon Exception");
//				e.printStackTrace();
//			}
		}
	}

	private static void runGardener() throws GameActionException {
		while (true) {
			try {
				float lol = 2.3f;
				if(rc.senseNearbyTrees(lol).length > 0){
				float bodyDistance = rc.getLocation().distanceTo(rc.senseNearbyTrees(lol)[0].getLocation());
				System.out.println("bodyDistance " + bodyDistance);
				System.out.println(rc.senseNearbyTrees(lol)[0]);
				rc.setIndicatorDot(rc.senseNearbyTrees(lol)[0].getLocation(), 255, 0, 0);
				}
				TreeInfo[] ti = rc.senseNearbyTrees(2.5f);
				pathfind(new MapLocation(23f, 5f), ti);	//For shrine and sparseTrees map
				//pathfind(new MapLocation(493f, 55f), ti);	//For Pathfinding map
				//pathfind(new MapLocation(284f, 177f), ti);	//For SparseForest map
				//pathfind(new MapLocation(402f, 208f), ti);	//For Teststuff map
				Clock.yield();
			} catch (GameActionException e) {
				System.out.println("Archon Exception");
				e.printStackTrace();
			}
		}
	}

	private static void runSoldier() throws GameActionException {
		while (true) {

		}
	}

	private static void runLumberjack() throws GameActionException {
		while (true) {

		}
	}

	private static void pathfind(MapLocation finalLoc, BodyInfo[] ti) throws GameActionException {
		MapLocation currentLocation = rc.getLocation();
		Direction dir = new Direction(currentLocation, finalLoc);
		
//		historyLocationCheck[rc.getRoundNum()%historyLocationCheck.length] = currentLocation;
//		historyLocations[rc.getRoundNum()%historyLocations.length] = currentLocation;
//		if(rc.getRoundNum() > 8){
//			historyDirectionCheck = new Direction(historyLocationCheck[(rc.getRoundNum()+1)%historyLocationCheck.length], currentLocation);
//			historyDirection = new Direction(historyLocations[(rc.getRoundNum()+1)%historyLocations.length], currentLocation);
//		}
		rc.setIndicatorDot(currentLocation, 0, 255, 255);
		System.out.println("\n\n" + rc.getRoundNum() + "\n\n");
//		if(rc.getRoundNum() > 8 && historyDirectionCheck.rotateLeftRads((float) PI).radians > dir.radians - 0.7f && historyDirectionCheck.rotateLeftRads((float) PI).radians < dir.radians + 0.7f){
//			dir = historyDirection;
//		}
		if (!tryMove(dir)) {
			System.out.println("\n\n0000dir:\n" + dir);
			dir = passBody(ti, dir);
			rc.move(dir);
		}
		System.out.println("1111\ndir: " + dir);
	}

	//Note.. current implementation prioritizes closer trees rather than trees that intersect "more" with the end location vector
	private static Direction passBody(BodyInfo[] ti, Direction dir) throws GameActionException {
		if(ti.length > 0){
			final float ROTATION_MARGIN = 0.2f;
			Direction lastLeftDirection = dir;
			boolean turningLeft = true;
			boolean rotatingLeftwards = false;
			
			/*If one has tried to move many times in different directions, then maybe the for 
			loop should break, so when counter reaches 0 for example, may not be necessary.*/
			int isFirstIteration = 0;	//Is 0 initially and first rotation left, 1 first right rotation and 2+ thereafter
			TreeMap<Float, BodyInfo> tm = sortByDistance(ti);	//SORT BY DISTANCE :D
		
			for (int i = 0; i < tm.size(); i++) {
				BodyInfo body = tm.pollFirstEntry().getValue();
				System.out.println(body);
				MapLocation bodyLoc = body.getLocation();
				
				Direction bodyDir = rc.getLocation().directionTo(bodyLoc);
				System.out.println("treeDir\n" + bodyDir);
				rc.setIndicatorDot(bodyLoc, 255, 0, 0);
				float bodyDistance = distance(bodyLoc);
				System.out.println("bodyDistance " + bodyDistance);
				System.out.println(rc.senseNearbyTrees(2)[0]);
				float bodyRadius = body.getRadius();
				//System.out.println("treeRadius " + bodyRadius);
				float thisRadius = rc.getType().bodyRadius;
				//System.out.println("robotRadius " + thisRadius);
				
				float extendedRadius = bodyRadius + thisRadius;
				//System.out.println("extendedRadius" + extendedRadius);
				
				float rotationRadians = (float) asin(extendedRadius/bodyDistance) + ROTATION_MARGIN;
				System.out.println("rotationRadians: "  + rotationRadians);
				
				//CHECK SO THAT CURRENT BODY IS IN THE WAY :(
				System.out.println("radians between dir and body: " + abs(dir.radiansBetween(bodyDir)));
				System.out.println("radians between body and adjusted direction: " + abs(dir.radiansBetween(bodyDir.rotateLeftRads(rotationRadians))));
				if(abs(dir.radiansBetween(bodyDir)) <= abs(dir.radiansBetween(bodyDir.rotateLeftRads(rotationRadians))) 
						|| abs(dir.radiansBetween(bodyDir)) <= abs(dir.radiansBetween(bodyDir.rotateRightRads(rotationRadians)))){
					System.out.println("\ncollision");
					float directionAngleFromTree = bodyDir.radiansBetween(dir);
					turningLeft = (directionAngleFromTree > 0) || rotatingLeftwards;
					if(turningLeft){
						System.out.println("turning left");
						System.out.println(bodyDir);
						dir = bodyDir.rotateLeftRads(rotationRadians);
						System.out.println(dir);
						lastLeftDirection = dir;
					} else {
						System.out.println("turning right");
						dir = bodyDir.rotateRightRads(rotationRadians);
						isFirstIteration++;
					}
						//Try to move again
					if(rc.canMove(dir)){
						break;
					} else if (isFirstIteration < 2){
						System.out.println("trying other direction");
						if(isFirstIteration == 0){
							System.out.println("here");
							dir = bodyDir.rotateRightRads(rotationRadians);
						} else {
							System.out.println("there");
							dir = bodyDir.rotateLeftRads(rotationRadians);
							lastLeftDirection = dir;
						}
						isFirstIteration = 2;
						if(rc.canMove(dir)){
							break;
						}
					}
					i = 0;
					//System.out.println("trying to redo forloop");
					dir = lastLeftDirection;
					System.out.println("redo forloop with direction:\n " + dir);
					rotatingLeftwards = true;
				}
			}
		}
		return dir;
	}

	private static TreeMap<Float, BodyInfo> sortByDistance(BodyInfo[] ti) {
		TreeMap<Float, BodyInfo> tm = new TreeMap<>();
		for(BodyInfo bi: ti){
			tm.put(rc.getLocation().distanceTo(bi.getLocation()), bi);
		}
		return tm;
	}

	//Probably unnecessary
	public static float distance(MapLocation endLoc) {
		System.out.println("(x, y)" + rc.getLocation());
		System.out.println("(z,b)" + endLoc);
		return (float) sqrt(pow(rc.getLocation().x - endLoc.x, 2) + pow(rc.getLocation().y - endLoc.y, 2));
	}

	private static boolean tryMove(Direction dir) throws GameActionException {
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}
		return false;
	}

	/*
	private static boolean tryMove(Direction dir) throws GameActionException {
		return tryMove(dir, 20, 3);
	}

	private static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

		// First, try intended direction
		if (rc.canMove(dir)) {
			rc.move(dir);
			return true;
		}

		// Now try a bunch of similar angles
		boolean moved = false;
		int currentCheck = 1;

		while (currentCheck <= checksPerSide) {
			// Try the offset of the left side
			if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
				rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
				return true;
			}
			// Try the offset on the right side
			if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
				rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
				return true;
			}
			// No move performed, try slightly further
			currentCheck++;
		}

		// A move never happened, so return false.
		return false;
	}
	*/
}
